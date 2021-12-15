package net.corda.libs.configuration.write

/**
 * @param name name of the package/component
 * @param version version of the package/component
 */
@Suppress("Deprecation")
@Deprecated("Deprecated in line with the deprecation of `ConfigWriter`.")
class CordaConfigurationVersion(val name: String, private val major: Int, private val minor: Int) {
    companion object {
        fun from(name: String, version: String): CordaConfigurationVersion {
            val versionNumber = ConfigVersionNumber.from(version)
            return CordaConfigurationVersion(name, versionNumber.major, versionNumber.minor)
        }
    }
    val version = "$major.$minor"
}
