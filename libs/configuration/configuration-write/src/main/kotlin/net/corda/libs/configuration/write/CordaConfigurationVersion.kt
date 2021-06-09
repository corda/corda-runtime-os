package net.corda.libs.configuration.write

/**
 * @param name name of the package/component
 * @param version version of the package/component
 */
class CordaConfigurationVersion(val name: String, private val major: Int, private val minor: Int) {
    val version = "$major.$minor"
}
