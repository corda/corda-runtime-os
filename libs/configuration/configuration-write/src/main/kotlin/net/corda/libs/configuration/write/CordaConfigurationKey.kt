package net.corda.libs.configuration.write

/**
 * @param identity User owning the package
 * @param packageVersion Package name and version
 * @param componentVersion Component name and version
 */
@Suppress("Deprecation")
@Deprecated("Deprecated in line with the deprecation of `ConfigWriter`.")
data class CordaConfigurationKey(
    val identity: String,
    val packageVersion: CordaConfigurationVersion,
    val componentVersion: CordaConfigurationVersion
)