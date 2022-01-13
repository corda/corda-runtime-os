package net.corda.libs.configuration.publish

/**
 * @param identity User owning the package
 * @param packageVersion Package name and version
 * @param componentVersion Component name and version
 */
data class CordaConfigurationKey(
    val identity: String,
    val packageVersion: CordaConfigurationVersion,
    val componentVersion: CordaConfigurationVersion
)