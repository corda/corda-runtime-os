package net.corda.rest.annotations

/**
 * Enum representing a registry of REST API versions.
 * It must be updated with any new Corda version for new path to become available.
 */
enum class RestApiVersion(val versionPath: String, val parentVersion: RestApiVersion?) {

    C5_0("v1", null),
    C5_1("v5_1", C5_0),

    MIN_SUPPORTED(C5_0.versionPath, C5_0.parentVersion),
    CURRENT(C5_1.versionPath, C5_1.parentVersion)
}