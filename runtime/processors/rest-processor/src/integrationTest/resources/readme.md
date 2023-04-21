## Open API compatibility testing

File [swaggerBaseline.json](./swaggerBaseline.json) represents a snapshot of Open API which REST Worker currently
provides.

There is also an integration test [OpenApiCompatibilityTest](../kotlin/net/corda/processors/rest/OpenApiCompatibilityTest.kt)
which asserts that Open API produced currently by a running HTTP Server matches to the baseline.

That said, any time `PluggableRestResource` interfaces are updated and/or parameters of HTTP Endpoints modified the `swaggerBaseline.json`
file need to be updated or else `OpenApiCompatibilityTest` will fail.

To update the `swaggerBaseline.json`, please run OSGi integration test:
`gradlew :processors:rest-processor:integrationTest` - if it fails it will report the differences detected
as well full snapshot of current Open API produced.

An easy way to extract the current API, compare against the baseline and update if necessary is:

- Copy the entire output of the integration test to a text editor of your choice
- Search for "Produced Open API content". Delete the text before this point
- Search for "Differences noted". Delete the text after this point
- You now have the current API. Copy this, and diff against the baseline file using a tool of your choice
- Check each difference and ensure all changes are as expected before updating the baseline file
