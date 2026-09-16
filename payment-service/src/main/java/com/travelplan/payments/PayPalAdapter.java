package com.travelplan.payments;
import org.springframework.stereotype.Component;
@Component public class PayPalAdapter implements ProviderAdapter {
 public String provider(){return "PAYPAL";}
 public ProviderResult connect(String reference,String key){if(reference==null||reference.isBlank())throw new IllegalArgumentException("Provider reference required");return new ProviderResult(reference,"ACTIVE","PayPal sandbox reference");}
}
