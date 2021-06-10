package net.corda.libs.configuration.read

interface CordaConfigurationUpdate {
    fun configurationUpdated(properties: List<String>)
}