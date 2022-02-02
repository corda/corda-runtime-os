package net.corda.libs.configuration.validation.impl

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.typesafe.config.ConfigRenderOptions
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.validation.ConfigurationValidationException
import net.corda.libs.configuration.validation.ConfigurationValidator
import net.corda.schema.configuration.SchemaProvider
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug

internal class ConfigurationValidatorImpl(private val schemaProvider: SchemaProvider) : ConfigurationValidator {

    private companion object {
        private val logger = contextLogger()
        private val REGISTERED_SCHEMES = listOf("https", "http")
    }

    private val schemaFactory = buildSchemaFactory()
    private val objectMapper = ObjectMapper()

    override fun validate(key: String, config: SmartConfig) {
        logger.info("Validating config for key $key")
        logger.debug {
            "Configuration to validate: ${
                config.toSafeConfig().root().render(ConfigRenderOptions.concise())
            }"
        }
        val schemaInput = schemaProvider.getSchema(key)
        val schema = schemaFactory.getSchema(schemaInput)
        val errors = schema.validate(config.toJsonNode())
        if (errors.isNotEmpty()) {
            val errorSet = errors.map { it.message }.toSet()
            logger.error(
                "Configuration validation failed for key $key. Errors: $errorSet. Full config: ${
                    config.toSafeConfig().root().render(
                        ConfigRenderOptions.concise()
                    )
                }"
            )
            throw ConfigurationValidationException(key, config, errorSet)
        }
    }

    private fun SmartConfig.toJsonNode(): JsonNode {
        val jsonString = this.root().render(ConfigRenderOptions.concise())
        return objectMapper.readTree(jsonString)
    }

    private fun buildSchemaFactory(): JsonSchemaFactory {
        val builder = JsonSchemaFactory.builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7))
        builder.uriFetcher(CordaURIFetcher(), REGISTERED_SCHEMES)
        return builder.build()
    }
}