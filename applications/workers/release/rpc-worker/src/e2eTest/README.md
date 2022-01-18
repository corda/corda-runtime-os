# HTTP RPC End-to-End (E2E) tests

These tests assume that RPC Worker Java process is running and healthy with all the required dependencies
satisfied like:
- Kafka Bus along with all the necessary topics created;
- DB Worker along with correctly setup Database;
- Anything else RPC Worker might need.

One way to set all those dependencies up is by using Docker Compose script, as described [here](../../../deploy/README.md).

Then these tests communicate using HTTP protocol using published OpenAPI to trigger some actions and assert side effects
by observing them through HTTP endpoints.

These tests are lightweight and only require `HttpRpcClient` to operate properly. They do not require any OSGi framework
provisions.