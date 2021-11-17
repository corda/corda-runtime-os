# Topic Schemas

The schemas for each topic a virtual node uses will be defined here.

These schemas should be updated here alongside their implementation in an effort to keep this document up-to-date.

### Kafka Auto-topic creation
**We must ensure to avoid setting auto-creation of topics is Kafka!**

This can lead to unnoticed errors in production.  We should adhere to the ethos that if a topic
we expect doesn't exist then that signifies a setup problem and should immediately cause an error.
