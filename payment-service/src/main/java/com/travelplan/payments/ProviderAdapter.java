package com.travelplan.payments;
public interface ProviderAdapter {
 String provider();
 ProviderResult connect(String reference, String idempotencyKey);
 record ProviderResult(String providerReference, String status, String displayMetadata) {}
}
