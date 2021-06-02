package net.corda.libs.configuration.write

/**
 * Records the name and version number of a package or component.
 */
class CordaConfigurationVersion(val name: String, private val major: Int, private val minor: Int) {
    val version = "$major.$minor"
}

