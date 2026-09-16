# Resilience rehearsal

Start the stack with `compose.scale.yml`, send repeated authenticated reads through the gateway, then stop one instance and confirm the remaining healthy instance continues serving requests. Record instance IDs, request IDs, latency, error counts, and the exact replica count. Do not claim database failover from this exercise; PostgreSQL and Neo4j remain single-container local databases until a replicated deployment is supplied.
