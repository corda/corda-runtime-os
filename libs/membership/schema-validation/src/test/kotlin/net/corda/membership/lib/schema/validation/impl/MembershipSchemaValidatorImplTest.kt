package net.corda.membership.lib.schema.validation.impl

import net.corda.libs.configuration.SmartConfig
import net.corda.membership.lib.schema.validation.MembershipSchemaValidationException
import net.corda.schema.membership.MembershipSchema
import net.corda.schema.membership.provider.MembershipSchemaProvider
import net.corda.v5.base.versioning.Version
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MembershipSchemaValidatorImplTest {
    private val schema = mock<MembershipSchema.GroupPolicySchema>()
    private val version = mock<Version>()
    private val rawSchema = "{}"
    private val groupPolicy = "{\"p2pParameters\": {\"tlsType\": \"OneWay\"}}"
    private val membershipSchemaProvider = mock<MembershipSchemaProvider> {
        on { getSchema(schema, version) } doReturn rawSchema.byteInputStream()
    }
    private val sslConfiguration = mock<SmartConfig> {
        on { getString("tlsType") } doReturn "ONE_WAY"
    }
    private val gatewayConfiguration = mock<SmartConfig> {
        on { getConfig("sslConfig") } doReturn sslConfiguration
    }
    private val impl = MembershipSchemaValidatorImpl(membershipSchemaProvider)


    @Test
    fun `validateGroupPolicy will pass if the TLS type of the group policy is correct`() {
        impl.validateGroupPolicy(
            schema,
            version,
            groupPolicy
        ) { gatewayConfiguration }
    }

    @Test
    fun `validateGroupPolicy will throw an exception if the TLS type of the group policy is wrong`() {
        whenever(sslConfiguration.getString("tlsType")).doReturn("MUTUAL")

        assertThrows<MembershipSchemaValidationException> {
            impl.validateGroupPolicy(
                schema,
                version,
                groupPolicy
            ) { gatewayConfiguration }
        }
    }

    @Test
    fun `validateGroupPolicy will pass if there is no P2P parameters`() {
        whenever(sslConfiguration.getString("tlsType")).doReturn("MUTUAL")

        impl.validateGroupPolicy(
            schema,
            version,
            "{}",
        ) { gatewayConfiguration }
    }

    @Test
    fun `validateGroupPolicy will pass if there is the TLS type is missing`() {
        whenever(sslConfiguration.getString("tlsType")).doReturn("MUTUAL")

        impl.validateGroupPolicy(
            schema,
            version,
            "{\"p2pParameters\": {}}",
        ) { gatewayConfiguration }
    }


    @Test
    fun `validateGroupPolicy will pass if there is theconfiguration getter was not supplied`() {
        whenever(sslConfiguration.getString("tlsType")).doReturn("MUTUAL")

        impl.validateGroupPolicy(
            schema,
            version,
            groupPolicy,
            null,
        )
    }
    /*

    override fun validateGroupPolicy(
        schema: MembershipSchema.GroupPolicySchema,
        version: Version,
        groupPolicy: String,
        configurationGetService: ((String) -> SmartConfig?)?,
    ) {

        val schemaInput = try {
            membershipSchemaProvider.getSchema(schema, version)
        } catch (ex: MembershipSchemaException) {
            val errReason = "Failed to retrieve the schema require to validate the group policy file."
            logger.error(errReason, ex)
            throw MembershipSchemaValidationException(VALIDATION_ERROR, ex, schema, listOf(errReason))
        }
        val groupPolicyJson = try {
            objectMapper.readTree(groupPolicy)
        } catch (ex: Exception) {
            val errReason = "Failed to parse group policy as valid JSON."
            logger.error(errReason, ex)
            throw MembershipSchemaValidationException(VALIDATION_ERROR, ex, schema, listOf(errReason))
        }
        validateJson(schema, schemaInput, groupPolicyJson)

        val groupPolicyTlsType = groupPolicyJson.get("p2pParameters")?.get("tlsType")?.asText()
        if ((configurationGetService != null) && (groupPolicyTlsType != null)) {
            // groupPolicyTlsType will be null for MGM group policies
            val clusterTlsType = TlsType.getClusterType(configurationGetService)
            if (groupPolicyTlsType != clusterTlsType.groupPolicyName) {
                throw MembershipSchemaValidationException(
                    VALIDATION_ERROR,
                    null,
                    schema,
                    listOf(
                        "Group policy TLS type must be the same as the cluster group policy",
                    )
                )
            }
        }
    }

     */
}