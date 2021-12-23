package net.corda.libs.configuration.publish

/**
 * @param name name of the package/component
 * @property version version of the package/component
 */
@Suppress("Deprecation")
@Deprecated("Deprecated in line with the deprecation of `ConfigWriter`.")
data class CordaConfigurationVersion(val name: String, val major: Int, val minor: Int) {
    companion object {
        fun from(name: String, version: String): CordaConfigurationVersion {
            val versionNumber = ConfigVersionNumber.from(version)
            return CordaConfigurationVersion(name, versionNumber.major, versionNumber.minor)
        }
    }
    val version = "$major.$minor"
}
