# Payment sandbox operations

Stripe sandbox uses a SetupIntent/PaymentIntent to collect and attach a PaymentMethod, following the [Stripe Payment Methods API](https://docs.stripe.com/payments/payment-methods) (API version `2025-06-30.basil`). PayPal sandbox uses the Vault setup-token flow described in the [PayPal Payment Tokens API](https://developer.paypal.com/api/payment-tokens/v3/setup-tokens-create/). The browser or provider SDK performs tokenization; this service receives only the resulting reference.

The payment service accepts provider references only; raw card numbers, CVV values, and provider secrets never enter the API. `STRIPE` and `PAYPAL` adapters validate and connect sandbox references, returning safe status metadata. Use an `Idempotency-Key` for retries and treat provider failures as controlled `409` responses. Configure real sandbox credentials through Vault under `secret/travel-plan/payments`.

Gateway deletion is rejected while methods reference it. Disable or detach methods first. User deletion invokes the cleanup contract and retains failed provider cleanup as a visible status for retry.
