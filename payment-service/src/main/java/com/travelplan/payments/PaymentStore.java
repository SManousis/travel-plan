package com.travelplan.payments;

import com.travelplan.payments.PaymentDomain.ConnectAttempt;
import com.travelplan.payments.PaymentDomain.DeleteAttempt;
import com.travelplan.payments.PaymentDomain.Conflict;
import com.travelplan.payments.PaymentDomain.Gateway;
import com.travelplan.payments.PaymentDomain.Method;
import com.travelplan.payments.PaymentDomain.NotFound;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
final class PaymentStore {
    private static final String METHOD_COLUMNS = "id,user_id,gateway_id,provider,provider_reference,display_label,"
            + "status,display_metadata,failure_code,failure_stage,idempotency_key,request_hash,created_at,updated_at";
    private final JdbcTemplate jdbc;
    private final Map<UUID, Gateway> localGateways = new ConcurrentHashMap<>();
    private final Map<UUID, Method> localMethods = new ConcurrentHashMap<>();

    PaymentStore() { this.jdbc = null; }

    @Autowired
    PaymentStore(ObjectProvider<JdbcTemplate> provider) { this.jdbc = provider.getIfAvailable(); }

    List<Gateway> gateways() {
        if (jdbc != null) return jdbc.query("SELECT id,provider,display_name,enabled,created_at FROM payment_gateways "
                + "ORDER BY created_at,id", (rs, row) -> gateway(rs));
        return localGateways.values().stream().sorted(Comparator.comparing(Gateway::createdAt).thenComparing(Gateway::id)).toList();
    }

    Gateway gateway(UUID id) {
        if (jdbc != null) {
            List<Gateway> found = jdbc.query("SELECT id,provider,display_name,enabled,created_at FROM payment_gateways "
                    + "WHERE id=?", (rs, row) -> gateway(rs), id);
            if (found.isEmpty()) throw new NotFound();
            return found.getFirst();
        }
        Gateway found = localGateways.get(id);
        if (found == null) throw new NotFound();
        return found;
    }

    Gateway createGateway(String provider, String displayName) {
        Gateway result = new Gateway(UUID.randomUUID(), provider, displayName, true, Instant.now());
        if (jdbc != null) {
            jdbc.update("INSERT INTO payment_gateways(id,provider,display_name,enabled,created_at) VALUES (?,?,?,?,?)",
                    result.id(), result.provider(), result.displayName(), result.enabled(), Timestamp.from(result.createdAt()));
        } else localGateways.put(result.id(), result);
        return result;
    }

    synchronized Gateway updateGateway(UUID id, String provider, String displayName) {
        Gateway old = gateway(id);
        if (!old.provider().equals(provider)) throw new Conflict("GATEWAY_PROVIDER_IMMUTABLE");
        Gateway result = new Gateway(id, old.provider(), displayName, old.enabled(), old.createdAt());
        if (jdbc != null) {
            if (jdbc.update("UPDATE payment_gateways SET display_name=? WHERE id=? AND provider=?",
                    displayName, id, provider) == 0) throw new NotFound();
        } else localGateways.put(id, result);
        return result;
    }

    synchronized void deleteGateway(UUID id) {
        gateway(id);
        if (jdbc != null) {
            try {
                if (jdbc.update("DELETE FROM payment_gateways WHERE id=?", id) == 0) throw new NotFound();
            } catch (DataIntegrityViolationException ex) {
                throw new Conflict("GATEWAY_IN_USE");
            }
        } else {
            if (localMethods.values().stream().anyMatch(m -> id.equals(m.gatewayId())))
                throw new Conflict("GATEWAY_IN_USE");
            localGateways.remove(id);
        }
    }

    List<Method> methods(UUID userId) {
        if (jdbc != null) {
            String sql = "SELECT " + METHOD_COLUMNS + " FROM payment_methods"
                    + (userId == null ? "" : " WHERE user_id=?") + " ORDER BY created_at,id";
            return userId == null ? jdbc.query(sql, (rs, row) -> method(rs))
                    : jdbc.query(sql, (rs, row) -> method(rs), userId);
        }
        return localMethods.values().stream().filter(m -> userId == null || userId.equals(m.userId()))
                .sorted(Comparator.comparing(Method::createdAt).thenComparing(Method::id)).toList();
    }

    Method method(UUID id) {
        if (jdbc != null) {
            List<Method> found = jdbc.query("SELECT " + METHOD_COLUMNS + " FROM payment_methods WHERE id=?",
                    (rs, row) -> method(rs), id);
            if (found.isEmpty()) throw new NotFound();
            return found.getFirst();
        }
        Method found = localMethods.get(id);
        if (found == null) throw new NotFound();
        return found;
    }

    synchronized ConnectAttempt beginConnect(UUID userId, UUID gatewayId, String reference, String label,
                                             String idempotencyKey, String requestHash) {
        Method previous = byIdempotencyKey(idempotencyKey);
        if (previous != null) {
            if (!requestHash.equals(previous.requestHash())) throw new Conflict("IDEMPOTENCY_KEY_REUSED");
            if ("PENDING".equals(previous.status())) {
                if (jdbc != null) {
                    int claimed = jdbc.update("UPDATE payment_methods SET updated_at=CURRENT_TIMESTAMP WHERE id=? "
                            + "AND status='PENDING' AND updated_at < CURRENT_TIMESTAMP - INTERVAL '30 seconds'",
                            previous.id());
                    return new ConnectAttempt(method(previous.id()), claimed == 1, false);
                }
                if (previous.updatedAt().isAfter(Instant.now().minusSeconds(30)))
                    return new ConnectAttempt(previous, false, false);
                Method claimed = withState(previous, "PENDING", previous.displayMetadata(), null, null);
                localMethods.put(claimed.id(), claimed);
                return new ConnectAttempt(claimed, true, false);
            }
            if ("FAILED".equals(previous.status()) && "CONNECT".equals(previous.failureStage())) {
                if (jdbc != null) {
                    int changed = jdbc.update("UPDATE payment_methods SET status='PENDING',failure_code=NULL,"
                            + "failure_stage=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='FAILED' "
                            + "AND failure_stage='CONNECT'", previous.id());
                    return new ConnectAttempt(method(previous.id()), changed == 1, false);
                }
                Method retry = withState(previous, "PENDING", previous.displayMetadata(), null, null);
                localMethods.put(retry.id(), retry);
                return new ConnectAttempt(retry, true, false);
            }
            return new ConnectAttempt(previous, false, false);
        }
        Gateway gateway = gateway(gatewayId);
        if (!gateway.enabled()) throw new Conflict("GATEWAY_DISABLED");
        if (duplicateReference(gateway.provider(), reference)) throw new Conflict("PAYMENT_METHOD_EXISTS");
        Instant now = Instant.now();
        Method pending = new Method(UUID.randomUUID(), userId, gatewayId, gateway.provider(), reference,
                label, "PENDING", null, null, null, idempotencyKey, requestHash, now, now);
        if (jdbc != null) {
            try {
                jdbc.update("INSERT INTO payment_methods(id,user_id,gateway_id,provider,provider_reference,"
                                + "display_label,status,display_metadata,failure_code,failure_stage,idempotency_key,"
                                + "request_hash,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                        pending.id(), pending.userId(), pending.gatewayId(), pending.provider(),
                        pending.providerReference(), pending.displayLabel(), pending.status(), null, null, null,
                        pending.idempotencyKey(), pending.requestHash(), Timestamp.from(pending.createdAt()));
            } catch (DataIntegrityViolationException ex) {
                Method concurrent = byIdempotencyKey(idempotencyKey);
                if (concurrent != null) {
                    if (!requestHash.equals(concurrent.requestHash())) throw new Conflict("IDEMPOTENCY_KEY_REUSED");
                    return new ConnectAttempt(concurrent, false, false);
                }
                throw new Conflict("PAYMENT_METHOD_EXISTS");
            }
        } else localMethods.put(pending.id(), pending);
        return new ConnectAttempt(jdbc == null ? pending : method(pending.id()), true, true);
    }

    synchronized Method completeConnect(Method claim, String displayMetadata) {
        if (jdbc != null) {
            int changed = jdbc.update("UPDATE payment_methods SET status='ACTIVE',display_metadata=?,"
                    + "failure_code=NULL,failure_stage=NULL,updated_at=CURRENT_TIMESTAMP "
                    + "WHERE id=? AND status='PENDING' AND updated_at=?",
                    displayMetadata, claim.id(), Timestamp.from(claim.updatedAt()));
            if (changed != 1) throw new Conflict("METHOD_STATE_CHANGED");
        } else {
            Method current = method(claim.id());
            if (!"PENDING".equals(current.status()) || !current.updatedAt().equals(claim.updatedAt()))
                throw new Conflict("METHOD_STATE_CHANGED");
            localMethods.put(claim.id(), withState(current, "ACTIVE", displayMetadata, null, null));
        }
        return method(claim.id());
    }

    void failConnect(Method claim, String code) { fail(claim, code, "CONNECT", "PENDING"); }

    synchronized DeleteAttempt beginDelete(UUID id) {
        Method old = method(id);
        if ("PENDING".equals(old.status())) throw new Conflict("METHOD_CONNECTION_IN_PROGRESS");
        if ("DELETION_PENDING".equals(old.status())) {
            if (jdbc != null) {
                boolean claimed = jdbc.update("UPDATE payment_methods SET updated_at=CURRENT_TIMESTAMP "
                        + "WHERE id=? AND status='DELETION_PENDING' "
                        + "AND updated_at < CURRENT_TIMESTAMP - INTERVAL '30 seconds'", id) == 1;
                return new DeleteAttempt(method(id), claimed);
            }
            if (old.updatedAt().isAfter(Instant.now().minusSeconds(30))) return new DeleteAttempt(old, false);
            Method claimed = withState(old, "DELETION_PENDING", old.displayMetadata(),
                    old.failureCode(), old.failureStage());
            localMethods.put(id, claimed);
            return new DeleteAttempt(claimed, true);
        }
        if (jdbc != null) {
            int changed = jdbc.update("UPDATE payment_methods SET status='DELETION_PENDING',"
                    + "updated_at=CURRENT_TIMESTAMP WHERE id=? AND status=?",
                    id, old.status());
            if (changed == 0) throw new Conflict("METHOD_STATE_CHANGED");
            return new DeleteAttempt(method(id), true);
        }
        Method claimed = withState(old, "DELETION_PENDING", old.displayMetadata(),
                old.failureCode(), old.failureStage());
        localMethods.put(id, claimed);
        return new DeleteAttempt(claimed, true);
    }

    synchronized void finishDelete(Method claim) {
        if (jdbc != null) {
            if (jdbc.update("DELETE FROM payment_methods WHERE id=? AND status='DELETION_PENDING' "
                    + "AND updated_at=?", claim.id(), Timestamp.from(claim.updatedAt())) == 0) {
                try { method(claim.id()); } catch (NotFound ignored) { return; }
                throw new Conflict("METHOD_STATE_CHANGED");
            }
        } else {
            Method current = method(claim.id());
            if (!"DELETION_PENDING".equals(current.status()) || !current.updatedAt().equals(claim.updatedAt()))
                throw new Conflict("METHOD_STATE_CHANGED");
            localMethods.remove(claim.id());
        }
    }

    void failDelete(Method claim, String code) { fail(claim, code, "DELETE", "DELETION_PENDING"); }

    synchronized Method updateLabel(UUID id, String label) {
        Method old = method(id);
        if ("PENDING".equals(old.status()) || "DELETION_PENDING".equals(old.status()) || "FAILED".equals(old.status())
                && "DELETE".equals(old.failureStage())) throw new Conflict("METHOD_DELETION_IN_PROGRESS");
        if (jdbc != null) {
            if (jdbc.update("UPDATE payment_methods SET display_label=?,updated_at=? WHERE id=? "
                            + "AND status NOT IN ('PENDING','DELETION_PENDING') "
                            + "AND (failure_stage IS NULL OR failure_stage<>'DELETE')",
                    label, Timestamp.from(Instant.now()), id) == 0) throw new Conflict("METHOD_DELETION_IN_PROGRESS");
        } else localMethods.put(id, new Method(old.id(), old.userId(), old.gatewayId(), old.provider(),
                old.providerReference(), label, old.status(), old.displayMetadata(), old.failureCode(),
                old.failureStage(), old.idempotencyKey(), old.requestHash(), old.createdAt(), Instant.now()));
        return method(id);
    }

    private synchronized void fail(Method claim, String code, String stage, String expectedStatus) {
        if (jdbc != null) jdbc.update("UPDATE payment_methods SET status='FAILED',failure_code=?,failure_stage=?,"
                + "updated_at=CURRENT_TIMESTAMP WHERE id=? AND status=? AND updated_at=?",
                code, stage, claim.id(), expectedStatus, Timestamp.from(claim.updatedAt()));
        else {
            Method current;
            try { current = method(claim.id()); } catch (NotFound ignored) { return; }
            if (expectedStatus.equals(current.status()) && current.updatedAt().equals(claim.updatedAt()))
                localMethods.put(claim.id(), withState(current, "FAILED", current.displayMetadata(), code, stage));
        }
    }

    private Method byIdempotencyKey(String key) {
        if (jdbc != null) {
            List<Method> found = jdbc.query("SELECT " + METHOD_COLUMNS + " FROM payment_methods WHERE idempotency_key=?",
                    (rs, row) -> method(rs), key);
            return found.isEmpty() ? null : found.getFirst();
        }
        return localMethods.values().stream().filter(m -> key.equals(m.idempotencyKey())).findFirst().orElse(null);
    }

    private boolean duplicateReference(String provider, String reference) {
        if (jdbc != null) return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM payment_methods WHERE provider=? AND provider_reference=?)",
                Boolean.class, provider, reference));
        return localMethods.values().stream().anyMatch(m -> provider.equals(m.provider())
                && reference.equals(m.providerReference()));
    }

    private static Gateway gateway(ResultSet rs) throws SQLException {
        return new Gateway((UUID) rs.getObject("id"), rs.getString("provider"), rs.getString("display_name"),
                rs.getBoolean("enabled"), rs.getTimestamp("created_at").toInstant());
    }

    private static Method method(ResultSet rs) throws SQLException {
        UUID gatewayId = (UUID) rs.getObject("gateway_id");
        if (gatewayId == null) throw new Conflict("LEGACY_METHOD_REQUIRES_GATEWAY");
        return new Method((UUID) rs.getObject("id"), (UUID) rs.getObject("user_id"), gatewayId,
                rs.getString("provider"), rs.getString("provider_reference"), rs.getString("display_label"),
                rs.getString("status"), rs.getString("display_metadata"), rs.getString("failure_code"),
                rs.getString("failure_stage"), rs.getString("idempotency_key"), rs.getString("request_hash"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private static Method withState(Method old, String status, String displayMetadata, String failureCode,
                                    String failureStage) {
        return new Method(old.id(), old.userId(), old.gatewayId(), old.provider(), old.providerReference(),
                old.displayLabel(), status, displayMetadata, failureCode, failureStage, old.idempotencyKey(),
                old.requestHash(), old.createdAt(), Instant.now());
    }
}
