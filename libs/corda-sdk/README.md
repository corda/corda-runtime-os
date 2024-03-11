# Corda SDK

The Corda SDK exposes Corda functionality as one or more Kotlin libraries. 
The software development in the title refers to the building of software tools that interact with Corda, not the development of CorDapps. 
As such, the SDK will expose functions that support both developer and operational interactions with Corda.  

The Corda SDK contains a Kotlin rendering of the Corda REST API. 
The versioning in the Corda REST API means it will continue to support deprecated operations until they are removed from the API. 
This library forms the basis for other libraries interacting with the REST API.

The SDK also contains a Kotlin library that packages Corda administrative functions. 
These functions may be the result of aggregating multiple API calls or contain new client-side logic. 
This library includes the declarative network capability and this is exposed through the runtime Gradle plugin. 
The library should also include high-level functions for bootstrapping Corda that are then exposed through the CLI to replace template logic currently residing in the Helm charts.
The end result is that these interfaces (Gradle plugin, CLI, test utilities) become "thin clients" with bulk of the logic in a single place.  