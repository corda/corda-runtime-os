# Corda REST Client

Client which allows calling into REST server from Kotlin code.

Unlike bare-bone HTTP client libraries (like: UniRest or OkHttp), client from this module offers native Kotlin syntax
for making remote calls.

I.e. the caller is just making call to an interface method and REST Client translates it to actual remote HTTP
invocation.

This offers better programming experience and enables compile time checks for HTTP methods invocations.

This module is for internal use at the moment for tools and services like CLI plugins.