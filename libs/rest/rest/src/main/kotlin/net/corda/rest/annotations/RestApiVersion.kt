package net.corda.rest.annotations

import java.util.EnumSet

/**
 * Enum representing a registry of REST API versions.
 * It must be updated with any new Corda version for new path to become available.
 */
enum class RestApiVersion(val versionPath: String, val parentVersion: RestApiVersion?) {

    C5_0("v1", null),
    C5_1("v5_1", C5_0),
    C5_2("v5_2", C5_1),
    C5_3("v5_3", C5_2),

    // Whenever new version is added, please re-visit `HttpResource.kt` and `RestEndpoint.kt` to change default
    // version aliases.
}

fun retrieveApiVersionsSet(minVersion: RestApiVersion, maxVersion: RestApiVersion): Set<RestApiVersion> {
    val result = EnumSet.noneOf(RestApiVersion::class.java)

    var current: RestApiVersion? = maxVersion
    while (current != null) {
        result.add(current)

        // Check if we have reached the bottom
        if (current == minVersion) {
            break
        }

        current = current.parentVersion
    }
    return result
}
