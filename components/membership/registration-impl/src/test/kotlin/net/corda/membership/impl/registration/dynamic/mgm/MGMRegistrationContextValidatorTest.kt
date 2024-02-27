package net.corda.membership.impl.registration.dynamic.mgm

import net.corda.configuration.read.ConfigurationGetService
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.PROTOCOL_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.URL_KEY
import net.corda.membership.lib.SelfSignedMemberInfo
import net.corda.membership.lib.schema.validation.MembershipSchemaValidationException
import net.corda.membership.lib.schema.validation.MembershipSchemaValidator
import net.corda.membership.lib.schema.validation.MembershipSchemaValidatorFactory
import net.corda.schema.configuration.ConfigKeys.P2P_GATEWAY_CONFIG
import net.corda.schema.membership.MembershipSchema
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.membership.MemberContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Calendar
import java.util.GregorianCalendar

class MGMRegistrationContextValidatorTest {

    private val membershipSchemaValidator: MembershipSchemaValidator = mock()
    private val membershipSchemaValidatorFactory: MembershipSchemaValidatorFactory = mock {
        on { createValidator() } doReturn membershipSchemaValidator
    }
    private val gatewayConfiguration = mock<SmartConfig> {
        on { getConfig("sslConfig") } doReturn mock
        on { getString("tlsType") } doReturn "ONE_WAY"
    }
    private val configurationGetService = mock<ConfigurationGetService> {
        on { getSmartConfig(P2P_GATEWAY_CONFIG) } doReturn gatewayConfiguration
    }

    private val validCertificateDate = GregorianCalendar(2022, Calendar.JULY, 22)
    private val clock = TestClock(validCertificateDate.toInstant())

    private val mgmRegistrationContextValidator = MGMRegistrationContextValidator(
        membershipSchemaValidatorFactory,
        configurationGetService = configurationGetService,
        clock = clock
    )

    companion object {
        private val trustrootCert = this::class.java.getResource("/r3Com.pem")!!.readText()

        private val validTestContext
            get() = mutableMapOf(
                SESSION_KEY_IDS.format(0) to "6819537D96BB",
                ECDH_KEY_ID to "EB8B54665D2E",
                REGISTRATION_PROTOCOL to "net.corda.membership.impl.registration.dynamic.member.DynamicMemberRegistrationService",
                SYNCHRONISATION_PROTOCOL to "net.corda.membership.impl.synchronisation.MemberSynchronisationServiceImpl",
                P2P_MODE to "P2P mode",
                SESSION_KEY_POLICY to "session key policy",
                PKI_SESSION to "session PKI property",
                PKI_TLS to "TLS PKI property",
                URL_KEY.format(0) to "https://localhost:8080",
                PROTOCOL_VERSION.format(0) to "1",
                TRUSTSTORE_SESSION.format(0) to trustrootCert,
                TRUSTSTORE_TLS.format(0) to trustrootCert
            )

        @JvmStatic
        fun contextKeys() = validTestContext.keys.toTypedArray()
    }

    @Test
    fun `schema validation is for MGM context schema`() {
        val contextSchemaCaptor = argumentCaptor<MembershipSchema.RegistrationContextSchema>()
        whenever(
            membershipSchemaValidator.validateRegistrationContext(
                contextSchemaCaptor.capture(),
                any(),
                any()
            )
        ).then {
            // do nothing
        }

        mgmRegistrationContextValidator.validate(validTestContext)

        verify(membershipSchemaValidator).validateRegistrationContext(any(), any(), any())
        val contextSchema = assertDoesNotThrow(contextSchemaCaptor::firstValue)
        assertThat(contextSchema).isInstanceOf(MembershipSchema.RegistrationContextSchema.Mgm::class.java)
    }

    @Test
    fun `schema validation is done on the same context passed to service`() {
        val contextCaptor = argumentCaptor<Map<String, String>>()
        whenever(
            membershipSchemaValidator.validateRegistrationContext(
                any(),
                any(),
                contextCaptor.capture()
            )
        ).then {
            // do nothing
        }

        val testContext = validTestContext
        mgmRegistrationContextValidator.validate(testContext)

        verify(membershipSchemaValidator).validateRegistrationContext(any(), any(), any())
        val contextSchema = assertDoesNotThrow(contextCaptor::firstValue)
        assertThat(contextSchema).isEqualTo(testContext)
    }

    @Test
    fun `schema validation failure is rethrown as a context validation exception`() {
        whenever(
            membershipSchemaValidator.validateRegistrationContext(
                any(),
                any(),
                any()
            )
        ).doThrow(MembershipSchemaValidationException::class)

        assertThrows<MGMRegistrationContextValidationException> {
            mgmRegistrationContextValidator.validate(validTestContext)
        }
        verify(membershipSchemaValidator).validateRegistrationContext(any(), any(), any())
    }

    @Test
    fun `unexpected runtime exception during schema validation is rethrown as a context validation exception`() {
        whenever(
            membershipSchemaValidator.validateRegistrationContext(
                any(),
                any(),
                any()
            )
        ).doThrow(RuntimeException::class)

        assertThrows<MGMRegistrationContextValidationException> {
            mgmRegistrationContextValidator.validate(validTestContext)
        }
        verify(membershipSchemaValidator).validateRegistrationContext(any(), any(), any())
    }

    @ParameterizedTest(name = "context validation fails if {0} is missing and exception is caught and rethrown")
    @MethodSource("contextKeys")
    fun `context validation fails if {0} is missing and exception is caught and rethrown`(
        input: String
    ) {
        val testContext = validTestContext
        testContext.remove(input)

        assertThrows<MGMRegistrationContextValidationException> {
            mgmRegistrationContextValidator.validate(testContext)
        }
    }

    @Test
    fun `context validation fails when registration protocol provided is invalid`() {
        val contextWithInvalidProtocol = validTestContext.plus("corda.group.protocol.registration" to "invalid")
        val exception = assertThrows<MGMRegistrationContextValidationException> {
            mgmRegistrationContextValidator.validate(contextWithInvalidProtocol)
        }
        assertThat(exception).hasMessageContaining("Invalid value for key $REGISTRATION_PROTOCOL in registration context.")
    }

    @Test
    fun `context validation fails when sync protocol provided is invalid`() {
        val contextWithInvalidProtocol = validTestContext.plus("corda.group.protocol.synchronisation" to "invalid")
        val exception = assertThrows<MGMRegistrationContextValidationException> {
            mgmRegistrationContextValidator.validate(contextWithInvalidProtocol)
        }
        assertThat(exception).hasMessageContaining("Invalid value for key $SYNCHRONISATION_PROTOCOL in registration context.")
    }

    @Test
    fun `context validation fails when session key ID is invalid`() {
        val contextWithInvalidSessionKey = validTestContext.plus(SESSION_KEY_IDS.format(0) to " ")
        val exception = assertThrows<MGMRegistrationContextValidationException> {
            mgmRegistrationContextValidator.validate(contextWithInvalidSessionKey)
        }
        assertThat(exception).hasMessageContaining("Invalid value for key ID ${SESSION_KEY_IDS.format(0)}.")
        assertThat(exception).hasMessageContaining("Hex string has length of 1 but should be 12 characters")
    }

    @Test
    fun `context validation fails when ECDH key ID is invalid`() {
        val contextWithInvalidECDHKey = validTestContext.plus(ECDH_KEY_ID to " ")
        val exception = assertThrows<MGMRegistrationContextValidationException> {
            mgmRegistrationContextValidator.validate(contextWithInvalidECDHKey)
        }
        assertThat(exception).hasMessageContaining("Invalid value for key ID $ECDH_KEY_ID.")
        assertThat(exception).hasMessageContaining("Hex string has length of 1 but should be 12 characters")
    }

    @Test
    fun `invalid TLS type will throw an exception`() {
        val context = validTestContext + (TLS_TYPE to "nop")
        assertThrows<MGMRegistrationContextValidationException> {
            mgmRegistrationContextValidator.validate(context)
        }
    }

    @Test
    fun `missing TLS type will default to one way`() {
        val context = validTestContext - TLS_TYPE

        mgmRegistrationContextValidator.validate(context)
    }

    @Test
    fun `wrong TLS type will throw an exception`() {
        val context = validTestContext + (TLS_TYPE to "mutual")
        assertThrows<MGMRegistrationContextValidationException> {
            mgmRegistrationContextValidator.validate(context)
        }
    }

    @Test
    fun `valid TLS type will not throw an exception`() {
        val context = validTestContext + (TLS_TYPE to "one_way")

        mgmRegistrationContextValidator.validate(context)
    }

    @Test
    fun `invalid TLS trust root will throw an exception`() {
        val context = validTestContext + (TRUSTSTORE_TLS.format(0) to "invalid-pem-payload")
        assertThrows<MGMRegistrationContextValidationException> {
            mgmRegistrationContextValidator.validate(context)
        }
    }

    @Test
    fun `invalid session trust root will throw an exception`() {
        val context = validTestContext + (TRUSTSTORE_SESSION.format(0) to "invalid-pem-payload")
        assertThrows<MGMRegistrationContextValidationException> {
            mgmRegistrationContextValidator.validate(context)
        }
    }

    @Test
    fun `certificate with invalid validity period as trust root will throw an exception`() {
        val futureDate = GregorianCalendar(2035, Calendar.FEBRUARY, 12)
        clock.setTime(futureDate.toInstant())

        assertThrows<MGMRegistrationContextValidationException> {
            mgmRegistrationContextValidator.validate(validTestContext)
        }
    }

    @Test
    fun `context validation passes when only endpoint changes were made`() {
        val newMemberContext = mock<MemberContext> {
            on { entries } doReturn mapOf(
                "corda.endpoints.0.connectionURL" to "https://localhost:1080",
                "corda.endpoints.0.protocolVersion" to "1",
            ).entries
        }
        val oldMemberContext = mock<MemberContext> {
            on { entries } doReturn mapOf(
                "corda.endpoints.0.connectionURL" to "https://localhost:8888",
                "corda.endpoints.0.protocolVersion" to "1",
            ).entries
        }
        val newContext = mock<SelfSignedMemberInfo> {
            on { memberProvidedContext } doReturn newMemberContext
        }
        val oldContext = mock<SelfSignedMemberInfo> {
            on { memberProvidedContext } doReturn oldMemberContext
        }

        assertDoesNotThrow {
            mgmRegistrationContextValidator.validateMemberContext(newContext, oldContext)
        }
    }

    @Test
    fun `context validation passes when only software and platform version changes were made`() {
        val newMemberContext = mock<MemberContext> {
            on { entries } doReturn mapOf(
                PLATFORM_VERSION to "50200",
                SOFTWARE_VERSION to "5.2.0",
            ).entries
        }
        val oldMemberContext = mock<MemberContext> {
            on { entries } doReturn mapOf(
                PLATFORM_VERSION to "50100",
                SOFTWARE_VERSION to "5.1.0",
            ).entries
        }
        val newContext = mock<SelfSignedMemberInfo> {
            on { memberProvidedContext } doReturn newMemberContext
        }
        val oldContext = mock<SelfSignedMemberInfo> {
            on { memberProvidedContext } doReturn oldMemberContext
        }

        assertDoesNotThrow {
            mgmRegistrationContextValidator.validateMemberContext(newContext, oldContext)
        }
    }

    @Test
    fun `context validation fails when non-endpoint changes were made`() {
        val newMemberContext = mock<MemberContext> {
            on { entries } doReturn mapOf(
                "corda.session.keys.0.id" to "ABC123456789",
            ).entries
        }
        val oldMemberContext = mock<MemberContext> {
            on { entries } doReturn mapOf(
                "corda.session.keys.0.id" to "XYZ123456789",
            ).entries
        }
        val newContext = mock<SelfSignedMemberInfo> {
            on { memberProvidedContext } doReturn newMemberContext
        }
        val oldContext = mock<SelfSignedMemberInfo> {
            on { memberProvidedContext } doReturn oldMemberContext
        }

        assertThrows<MGMRegistrationContextValidationException> {
            mgmRegistrationContextValidator.validateMemberContext(newContext, oldContext)
        }
    }

    @Test
    fun `group policy validation passes when no changes were made`() {
        val registrationContext = mapOf(
            "corda.session.keys.0.id" to "XYZ123456789",
            "corda.group.protocol.registration" to "Dynamic",
        )
        val groupPolicy = mock<LayeredPropertyMap> {
            on { entries } doReturn mapOf(
                "protocol.registration" to "Dynamic",
            ).entries
        }

        assertDoesNotThrow {
            mgmRegistrationContextValidator.validateGroupPolicy(registrationContext, groupPolicy)
        }
    }

    @Test
    fun `group policy validation fails when group policy related changes were made`() {
        val registrationContext = mapOf(
            "corda.session.keys.0.id" to "XYZ123456789",
            "corda.group.protocol.registration" to "Static",
        )
        val groupPolicy = mock<LayeredPropertyMap> {
            on { entries } doReturn mapOf(
                "protocol.registration" to "Dynamic",
            ).entries
        }

        assertThrows<MGMRegistrationContextValidationException> {
            mgmRegistrationContextValidator.validateGroupPolicy(registrationContext, groupPolicy)
        }
    }
}
