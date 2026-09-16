package com.travelplan.payments;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import java.time.Instant; import java.util.*; import java.util.concurrent.ConcurrentHashMap; import org.springframework.http.*; import org.springframework.web.bind.annotation.*;
@RestController public class PaymentController {
 private final Map<UUID,Gateway> gateways=new ConcurrentHashMap<>(); private final Map<UUID,PaymentMethod> methods=new ConcurrentHashMap<>();
 @GetMapping("/api/payment-gateways") Collection<Gateway> gateways(){return gateways.values();}
 @PostMapping("/api/payment-gateways") ResponseEntity<Gateway> createGateway(@Valid @RequestBody GatewayWrite b){var g=new Gateway(UUID.randomUUID(),b.provider(),b.displayName(),true,Instant.now());gateways.put(g.id(),g);return ResponseEntity.status(201).body(g);}
 @GetMapping("/api/payment-gateways/{id}") Gateway gateway(@PathVariable UUID id){return gatewayOf(id);}
 @PatchMapping("/api/payment-gateways/{id}") Gateway updateGateway(@PathVariable UUID id,@Valid @RequestBody GatewayWrite b){gatewayOf(id);var g=new Gateway(id,b.provider(),b.displayName(),true,Instant.now());gateways.put(id,g);return g;}
 @DeleteMapping("/api/payment-gateways/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteGateway(@PathVariable UUID id){gatewayOf(id);if(methods.values().stream().anyMatch(m->m.gatewayId().equals(id)))throw new Conflict("GATEWAY_IN_USE");gateways.remove(id);}
 @GetMapping("/api/payment-methods") Collection<PaymentMethod> methods(@RequestParam(required=false) UUID userId){return methods.values().stream().filter(m->userId==null||m.userId().equals(userId)).toList();}
 @PostMapping("/api/payment-methods") ResponseEntity<PaymentMethod> createMethod(@Valid @RequestBody MethodWrite b){gatewayOf(b.gatewayId());var m=new PaymentMethod(UUID.randomUUID(),b.userId(),b.gatewayId(),b.providerReference(),b.displayLabel(),"ACTIVE",Instant.now());methods.put(m.id(),m);return ResponseEntity.status(201).body(m);}
 @GetMapping("/api/payment-methods/{id}") PaymentMethod method(@PathVariable UUID id){var m=methods.get(id);if(m==null)throw new NotFound();return m;}
 @PatchMapping("/api/payment-methods/{id}") PaymentMethod updateMethod(@PathVariable UUID id,@RequestBody LabelWrite b){var m=method(id);var n=new PaymentMethod(m.id(),m.userId(),m.gatewayId(),m.providerReference(),b.displayLabel(),m.status(),m.createdAt());methods.put(id,n);return n;}
 @DeleteMapping("/api/payment-methods/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteMethod(@PathVariable UUID id){method(id);methods.remove(id);}
 private Gateway gatewayOf(UUID id){var g=gateways.get(id);if(g==null)throw new NotFound();return g;}
 @ExceptionHandler(NotFound.class) ResponseEntity<Error> missing(){return ResponseEntity.status(404).body(new Error("NOT_FOUND","Resource not found"));}
 @ExceptionHandler(Conflict.class) ResponseEntity<Error> conflict(Conflict e){return ResponseEntity.status(409).body(new Error(e.code,"Request conflicts with current state"));}
 record Gateway(UUID id,String provider,String displayName,boolean enabled,Instant createdAt){} record PaymentMethod(UUID id,UUID userId,UUID gatewayId,String providerReference,String displayLabel,String status,Instant createdAt){} record GatewayWrite(@NotBlank String provider,@NotBlank String displayName){} record MethodWrite(@NotNull UUID userId,@NotNull UUID gatewayId,@NotBlank String providerReference,String displayLabel){} record LabelWrite(@NotBlank String displayLabel){} record Error(String code,String message){} static class NotFound extends RuntimeException{} static class Conflict extends RuntimeException{final String code;Conflict(String c){code=c;}}
}
