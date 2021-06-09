package net.corda.libs.configuration.write

/**
 * @param identity User owning the package
 * @param packageVersion Package name and version
 * @param componentVersion Component name and version
 */
data class CordaConfigurationKey(
    val identity: String,
    val packageVersion: CordaConfigurationVersion,
    val componentVersion: CordaConfigurationVersion
) : Comparable<CordaConfigurationKey> {

    override fun compareTo(other: CordaConfigurationKey) = compareValuesBy(this,
        other,
        { it.identity },
        { it.packageVersion.name },
        { it.packageVersion.version },
        { it.componentVersion.name },
        { it.componentVersion.version })
}
