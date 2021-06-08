package net.corda.libs.configuration.write

/**
 * @param name name of the package/component
 * @param version version of the package/component
 */
data class CordaConfigurationVersion(val name: String, val version: String)
