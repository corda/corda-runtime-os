package net.corda.membership.impl.registration.dynamic.mgm

import net.corda.configuration.read.ConfigurationGetService
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.lib.MemberInfoExtension.Companion.PROTOCOL_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.URL_KEY
import net.corda.membership.lib.schema.validation.MembershipSchemaValidationException
import net.corda.membership.lib.schema.validation.MembershipSchemaValidator
import net.corda.membership.lib.schema.validation.MembershipSchemaValidatorFactory
import net.corda.schema.configuration.ConfigKeys.P2P_GATEWAY_CONFIG
import net.corda.schema.membership.MembershipSchema
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

    private val mgmRegistrationContextValidator = MGMRegistrationContextValidator(
        membershipSchemaValidatorFactory,
        configurationGetService = configurationGetService,
    )

    companion object {
        private val validTestContext
            get() = mutableMapOf(
                SESSION_KEY_ID to "session key",
                ECDH_KEY_ID to "ECDH key",
                REGISTRATION_PROTOCOL to "registration protocol",
                SYNCHRONISATION_PROTOCOL to "synchronisation protocol",
                P2P_MODE to "P2P mode",
                SESSION_KEY_POLICY to "session key policy",
                PKI_SESSION to "session PKI property",
                PKI_TLS to "TLS PKI property",
                URL_KEY.format(0) to "https://localhost:8080",
                PROTOCOL_VERSION.format(0) to "1",
                TRUSTSTORE_SESSION.format(0) to "session truststore",
                TRUSTSTORE_TLS.format(0) to "tls truststore"
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
}
