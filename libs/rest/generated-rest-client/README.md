# Generated Rest Client

This module takes the OpenAPI specification and generates an OkHttp rest client from it.  
This client can then be used, by the SDK for example, to send requests to a running Corda instance.

## Gradle lifecycle

Task ordering, just run the last one manually and it will invoke the earlier ones:
- Run task `deletePreviouslyGeneratedCode` to delete the generated code.
- Run task `openApiGenerate` to generate new code in the build directory.
- Run task `copyGenerated` to move the generated code into the main directory.

### Re-apply workarounds

During initial implementation we found a few issues where the spec and observed behaviour differ and result in exceptions being thrown. 
We've added some test cases to cover them, so if your build is failing with "Has the generated api been re-generated? Re-apply workaround"
take a look at the test cases, as there are links to JIRA tickets with details.  

## DTO

There are a small number of data classes which don't already exist in the codebase, but are listed in the spec. We will need to maintain those.
