# Generated REST Client

This module takes the OpenAPI specification and generates an OkHttp REST client from it.  
This client can then be used, by the SDK for example, to send requests to a running Corda instance.

## Gradle lifecycle

On the `compileKotlin` task we generated the client code. Task ordering:
- Run task `openApiGenerate` to generate new code in the build directory.
- Run task `applyWorkarounds` to apply any necessary workarounds to the generated code. This runs subtasks for known issues.

### Re-apply workarounds

During initial implementation we found a few issues where the spec and observed behaviour differ and result in exceptions being thrown. 
We've added some test cases to cover them, so if your build is failing with "Has the generated api been re-generated? Re-apply workaround"
take a look at the test cases, as there are links to JIRA tickets with details.  
The `applyWorkarounds` task should have taken care of this, but may have become out of date.

## Limitations

The CertificateApi has a known limitation where we are unable to send a list of files.  
The spec allows for a list of files to be sent, and you can achieve this via Swagger and cURL, but the generated client does not support this.  
The workaround is to send the files one at a time. If you provide a list of files, only the first file will be sent.

## DTO

There are a small number of data classes which don't already exist in the codebase, but are listed in the spec. We will need to maintain those.
