# REST End-to-End (E2E) tests

These tests assume that REST Worker Java process is running and healthy with all the required dependencies
satisfied like:
- Kafka Bus along with all the necessary topics created;
- DB Worker along with correctly setup Database;
- Anything else REST Worker might need.

One way to set all those dependencies up is by using local setup with K8s, as described [here](https://github.com/corda/corda-runtime-os/wiki/Local-development-with-Kubernetes).

Then these tests communicate using HTTP protocol using published OpenAPI to trigger some actions and assert side effects
by observing them through HTTP endpoints.

These tests are lightweight and only require `HttpRestClient` to operate properly. They do not require any OSGi framework
provisions.