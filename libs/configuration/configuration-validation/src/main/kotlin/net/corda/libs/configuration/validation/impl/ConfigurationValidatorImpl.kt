package net.corda.libs.configuration.validation.impl

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.ApplyDefaultsStrategy
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SchemaValidatorsConfig
import com.networknt.schema.SpecVersion
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.validation.ConfigurationSchemaFetchException
import net.corda.libs.configuration.validation.ConfigurationValidationException
import net.corda.libs.configuration.validation.ConfigurationValidator
import net.corda.schema.configuration.provider.SchemaProvider
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.versioning.Version
import java.io.InputStream

internal class ConfigurationValidatorImpl(private val schemaProvider: SchemaProvider) : ConfigurationValidator {

    private companion object {
        private val logger = contextLogger()
        private val REGISTERED_SCHEMES = listOf("https", "http")
    }

    private val schemaFactory = buildSchemaFactory()
    private val objectMapper = ObjectMapper()

    override fun validate(key: String, version: Version, config: SmartConfig, applyDefaults: Boolean) : SmartConfig {
        logger.info("Validating config for key $key with schema version $version")
        logger.debug {
            "Configuration to validate: ${
                config.toSafeConfig().root().render(ConfigRenderOptions.concise())
            }"
        }
        //jsonNode is updated in place by walker when [applyDefaults] is true
        val jsonNode = config.toJsonNode()
        val errors = try {
            // Note that the JSON schema library does lazy schema loading, so schema retrieval issues may not manifest
            // until the validation stage.
            val schemaInput = schemaProvider.getSchema(key, version)
            val schema = getSchema(schemaInput, applyDefaults)
            logger.debug { "Schema to validate against: $schema" }
            schema.walk(jsonNode, true).validationMessages
        } catch (e: Exception) {
            val message = "Could not retrieve the schema for key $key at schema version $version: ${e.message}"
            logger.error(message, e)
            throw ConfigurationSchemaFetchException(message, e)
        }
        if (errors.isNotEmpty()) {
            val errorSet = errors.map { it.message }.toSet()
            logger.error(
                "Configuration validation failed for key $key at schema version $version. Errors: $errorSet."
            )
            throw ConfigurationValidationException(key, version, config, errorSet).also {
                logger.debug { it.fullErrorDetail() }
            }
        }

        return config.factory.create(ConfigFactory.parseString(jsonNode.toString()))
    }

    private fun getSchema(schemaInput: InputStream, applyDefaults: Boolean): JsonSchema {
        val schemaValidatorsConfig = SchemaValidatorsConfig()
        schemaValidatorsConfig.applyDefaultsStrategy = ApplyDefaultsStrategy(applyDefaults, false, false)
        return schemaFactory.getSchema(schemaInput, schemaValidatorsConfig)
    }

    private fun SmartConfig.toJsonNode(): JsonNode {
        val jsonString = this.root().render(ConfigRenderOptions.concise())
        return objectMapper.readTree(jsonString)
    }

    private fun buildSchemaFactory(): JsonSchemaFactory {
        val builder = JsonSchemaFactory.builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7))
        builder.uriFetcher(CordaURIFetcher(schemaProvider), REGISTERED_SCHEMES)
        return builder.build()
    }
}