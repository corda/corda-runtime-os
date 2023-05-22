package net.corda.membership.lib.registration

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.common.RegistrationRequestDetails
import net.corda.membership.lib.registration.RegistrationRequestHelpers.getPreAuthToken
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class RegistrationRequestHelpersTest {
    private val preAuthToken = UUID(0, 1)
    private val context = KeyValuePairList(listOf(KeyValuePair(PRE_AUTH_TOKEN, preAuthToken.toString())))
    private val registrationRequestDetails = mock<RegistrationRequestDetails>()

    @Test
    fun `Pre-auth token can be parsed from registration request if present`() {
        whenever(
            registrationRequestDetails.registrationContext
        ) doReturn context

        val result = assertDoesNotThrow {
            registrationRequestDetails.getPreAuthToken()
        }

        Assertions.assertThat(result).isEqualTo(preAuthToken)
    }

    @Test
    fun `Pre-auth token from member context is null if not set`() {

        val result = assertDoesNotThrow {
            registrationRequestDetails.getPreAuthToken()
        }

        Assertions.assertThat(result).isNull()
    }
}