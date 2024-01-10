package net.corda.libs.configuration.validation

interface ExternalChannelsConfigValidator {

    fun validate(externalChannelsConfig: Collection<String>)

    fun validate(externalChannelsConfig: String)
}
