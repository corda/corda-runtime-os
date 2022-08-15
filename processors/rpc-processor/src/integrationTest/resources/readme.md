## Open API compatibility testing

File [swaggerBaseline.json](./swaggerBaseline.json) represents a snapshot of Open API which HTTP RPC Worker currently
provides.

There is also an integration test [OpenApiCompatibilityTest](../kotlin/net/corda/processors/rpc/OpenApiCompatibilityTest.kt)
which asserts that Open API produced currently by a running HTTP Server matches to the baseline.

That said, any time `PluggableRpcOps` interfaces are updated and/or parameters of HTTP Endpoints modified the `swaggerBaseline.json`
file need to be updated or else `OpenApiCompatibilityTest` will fail.

To update the `swaggerBaseline.json`, please run OSGi integration test:
`gradlew :processors:rpc-processor:integrationTest` - if it fails it will report the differences detected
as well full snapshot of current Open API produced.