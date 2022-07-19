package net.corda.membership.lib.schema.validation.impl

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.uri.URIFetcher
import net.corda.membership.lib.schema.validation.MembershipSchemaFetchException
import net.corda.membership.lib.schema.validation.MembershipSchemaValidationException
import net.corda.membership.lib.schema.validation.MembershipSchemaValidator
import net.corda.schema.membership.MembershipSchema
import net.corda.schema.membership.provider.MembershipSchemaException
import net.corda.schema.membership.provider.MembershipSchemaProvider
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.versioning.Version
import java.io.InputStream
import java.net.URI

class MembershipSchemaValidatorImpl(
    private val membershipSchemaProvider: MembershipSchemaProvider
) : MembershipSchemaValidator {
    private companion object {
        private val REGISTERED_SCHEMES = listOf("https", "http")
        val logger = contextLogger()
    }

    private val schemaFactory = buildSchemaFactory()
    private val objectMapper = ObjectMapper()

    override fun validateGroupPolicy(
        schema: MembershipSchema.GroupPolicySchema,
        version: Version,
        groupPolicy: String
    ) {
        val schemaInput = try {
            membershipSchemaProvider.getSchema(schema, version)
        } catch (ex: MembershipSchemaException) {
            val err = "Failed to retrieve the schema require to validate the group policy file."
            logger.error(err, ex)
            throw MembershipSchemaValidationException(err, ex)
        }
        val groupPolicyJson = try {
            objectMapper.readTree(groupPolicy)
        } catch (ex: Exception) {
            val err = "Failed to parse group policy as valid JSON."
            logger.error(err, ex)
            throw MembershipSchemaValidationException(err, ex)
        }
        validateJson(schemaInput, groupPolicyJson)
    }

    private fun validateJson(
        schemaInput: InputStream,
        json: JsonNode
    ) {
        val jsonSchema = schemaFactory.getSchema(schemaInput)
        val errors = jsonSchema.walk(json, true)
        if (errors.validationMessages.isNotEmpty()) {
            throw MembershipSchemaValidationException("Schema validation failed. ${errors.validationMessages.joinToString()}")
        }
    }

    override fun validateRegistrationContext(
        schema: MembershipSchema.RegistrationContextSchema,
        version: Version,
        registrationContext: Map<String, String>
    ) {
        val schemaInput = try {
            membershipSchemaProvider.getSchema(schema, version)
        } catch (ex: MembershipSchemaException) {
            val err = "Failed to retrieve the schema require to validate the registration context."
            logger.error(err, ex)
            throw MembershipSchemaValidationException(err, ex)
        }
        val contextAsJson = try {
            objectMapper.readTree(
                objectMapper.writeValueAsString(
                    registrationContext
                )
            )
        } catch (ex: Exception) {
            val err = "Failed to map registration context to JSON."
            logger.error(err, ex)
            throw MembershipSchemaValidationException(err, ex)
        }
        validateJson(schemaInput, contextAsJson)
    }

    private fun buildSchemaFactory(): JsonSchemaFactory {
        val builder = JsonSchemaFactory.builder(
            JsonSchemaFactory.getInstance(
                SpecVersion.VersionFlag.V7
            )
        )
        builder.uriFetcher(MembershipSchemaURIFetcher(membershipSchemaProvider), REGISTERED_SCHEMES)
        return builder.build()
    }

    private class MembershipSchemaURIFetcher(
        private val membershipSchemaProvider: MembershipSchemaProvider
    ) : URIFetcher {
        private companion object {
            private const val CORDA_SCHEMA_URL = "https://corda.r3.com/"
        }

        override fun fetch(uri: URI?): InputStream {
            if (uri == null) {
                throw MembershipSchemaFetchException("Cannot fetch membership schema for null URI.")
            }
            val resource = uri.toString().removePrefix(CORDA_SCHEMA_URL)
            return try {
                membershipSchemaProvider.getSchemaFile(resource)
            } catch (e: MembershipSchemaException) {
                throw MembershipSchemaFetchException("Could not fetch schema at resource $resource", e)
            }
        }
    }
}