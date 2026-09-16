package com.travelplan.users;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.annotation.PostConstruct;

@Service
public class UserStore {
    private final Map<UUID, UserDomain.User> users = new ConcurrentHashMap<>();
    private final Map<UUID, UserDomain.Traveler> travelers = new ConcurrentHashMap<>();
    private final Map<String, Object> idempotent = new ConcurrentHashMap<>();
    private final RevocationClient revocations;
    private final JdbcTemplate jdbc;
    public UserStore(RevocationClient revocations, ObjectProvider<JdbcTemplate> provider) { this.revocations = revocations; this.jdbc = provider.getIfAvailable(); }
    @PostConstruct void loadPersisted() { if (jdbc == null) return; try { jdbc.query("select id,email,first_name,last_name,role,status,created_at from users", r -> { var u=new UserDomain.User((UUID)r.getObject("id"),r.getString("email"),r.getString("first_name"),r.getString("last_name"),UserDomain.Role.valueOf(r.getString("role")),UserDomain.Status.valueOf(r.getString("status")),r.getTimestamp("created_at").toInstant()); users.put(u.id(),u); }); jdbc.query("select id,user_id,passport_number,nationality,created_at from travelers",r->{var t=new UserDomain.Traveler((UUID)r.getObject("id"),(UUID)r.getObject("user_id"),r.getString("passport_number"),r.getString("nationality"),r.getTimestamp("created_at").toInstant());travelers.put(t.id(),t);}); } catch (RuntimeException ignored) {} }

    public UserDomain.User create(String email, String first, String last, UserDomain.Role role) {
        if (users.values().stream().anyMatch(u -> u.email().equalsIgnoreCase(email))) throw new Conflict("EMAIL_EXISTS");
        UserDomain.User user = new UserDomain.User(UUID.randomUUID(), email.toLowerCase(Locale.ROOT), first, last, role, UserDomain.Status.ACTIVE, Instant.now());
        users.put(user.id(), user); if (jdbc != null) jdbc.update("insert into users(id,email,first_name,last_name,role,status,created_at) values (?,?,?,?,?,?,?)",user.id(),user.email(),user.firstName(),user.lastName(),user.role().name(),user.status().name(),user.createdAt()); return user;
    }
    public List<UserDomain.User> list() { return users.values().stream().sorted(Comparator.comparing(UserDomain.User::createdAt).thenComparing(UserDomain.User::id)).toList(); }
    public UserDomain.User get(UUID id) { UserDomain.User u = users.get(id); if (u == null) throw new NotFound(); return u; }
    public UserDomain.User update(UUID id, String email, String first, String last, UserDomain.Role role, UserDomain.Status status) {
        UserDomain.User old = get(id);
        if (email != null && users.values().stream().anyMatch(u -> !u.id().equals(id) && u.email().equalsIgnoreCase(email))) throw new Conflict("EMAIL_EXISTS");
        UserDomain.User next = new UserDomain.User(id, email == null ? old.email() : email.toLowerCase(Locale.ROOT), first == null ? old.firstName() : first, last == null ? old.lastName() : last, role == null ? old.role() : role, status == null ? old.status() : status, old.createdAt());
        if (old.role() == UserDomain.Role.ADMIN && next.role() != UserDomain.Role.ADMIN && users.values().stream().filter(u -> u.role() == UserDomain.Role.ADMIN && u.status() == UserDomain.Status.ACTIVE).count() <= 1) throw new Conflict("LAST_ADMIN");
        users.put(id, next); if (jdbc != null) jdbc.update("update users set email=?,first_name=?,last_name=?,role=?,status=? where id=?",next.email(),next.firstName(),next.lastName(),next.role().name(),next.status().name(),id); return next;
    }
    public void delete(UUID id) { UserDomain.User u = get(id); if (u.role() == UserDomain.Role.ADMIN && u.status() == UserDomain.Status.ACTIVE && users.values().stream().filter(x -> x.role() == UserDomain.Role.ADMIN && x.status() == UserDomain.Status.ACTIVE).count() <= 1) throw new Conflict("LAST_ADMIN"); revocations.revoke(id); users.put(id, new UserDomain.User(u.id(), u.email(), u.firstName(), u.lastName(), u.role(), UserDomain.Status.DISABLED, u.createdAt())); if (jdbc != null) jdbc.update("update users set status=? where id=?","DISABLED",id); travelers.values().removeIf(t -> t.userId().equals(id)); }
    public UserDomain.Traveler createTraveler(UUID userId, String passport, String nationality) { get(userId); UserDomain.Traveler t = new UserDomain.Traveler(UUID.randomUUID(), userId, passport, nationality, Instant.now()); travelers.put(t.id(), t); if (jdbc != null) jdbc.update("insert into travelers(id,user_id,passport_number,nationality,created_at) values (?,?,?,?,?)",t.id(),t.userId(),t.passportNumber(),t.nationality(),t.createdAt()); return t; }
    public List<UserDomain.Traveler> travelers(UUID userId) { return travelers.values().stream().filter(t -> userId == null || t.userId().equals(userId)).sorted(Comparator.comparing(UserDomain.Traveler::createdAt)).toList(); }
    public UserDomain.Traveler traveler(UUID id) { UserDomain.Traveler t = travelers.get(id); if (t == null) throw new NotFound(); return t; }
    public void deleteTraveler(UUID id) { traveler(id); travelers.remove(id); }
    public Object idempotent(String key) { return key == null || key.isBlank() ? null : idempotent.get(key); }
    public void remember(String key, Object value) { if (key != null && !key.isBlank()) idempotent.putIfAbsent(key, value); }
    public static class NotFound extends RuntimeException {}
    public static class Conflict extends RuntimeException { public final String code; public Conflict(String code) { this.code = code; } }
}
