# Optimization delivery log

## 1. Strict production release resolution

Production chat now requires an enabled, published channel matching the requested Agent.
Both web chat and IM use the same release validation. The console selects published channels.
Draft execution remains available through the Agent debug API. No database migration is needed.

Validation: backend `mvn -o test`; frontend build with Node 22.
