package com.travelplan.payments;
import org.springframework.stereotype.Component;
@Component public class StripeAdapter implements ProviderAdapter {
 public String provider(){return "STRIPE";}
 public ProviderResult connect(String reference,String key){if(reference==null||reference.isBlank())throw new IllegalArgumentException("Provider reference required");return new ProviderResult(reference,"ACTIVE","Stripe sandbox reference");}
}
