package net.corda.rest.annotations

/**
 * Enum representing a registry of REST API versions.
 * It must be updated with any new Corda version for new path to become available.
 */
enum class RestApiVersion(val versionPath: String, val parentVersion: RestApiVersion?) {

    C5_0("v1", null),
    C5_1("v5_1", C5_0),

    // Whenever new version is added, please re-visit `HttpResource.kt` and `RestEndpoint.kt` to change default
    // version aliases.
}