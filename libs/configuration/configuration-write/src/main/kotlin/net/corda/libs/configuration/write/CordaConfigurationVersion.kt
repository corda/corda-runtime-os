package net.corda.libs.configuration.write

/**
 * Records the name and version number of a package or component.
 * If [major] and [minor] are null then this represents the default
 * values for the package or component.
 */
class CordaConfigurationVersion(val name: String, val major: Int? = null, val minor: Int? = null) {
    val version = "${major ?: 0}.${minor ?: 0}"
}

