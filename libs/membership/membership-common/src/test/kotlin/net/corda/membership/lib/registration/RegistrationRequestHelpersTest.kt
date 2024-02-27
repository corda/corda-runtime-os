package net.corda.membership.lib.registration

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.SignedData
import net.corda.data.membership.common.RegistrationRequestDetails
import net.corda.membership.lib.ContextDeserializationException
import net.corda.membership.lib.registration.RegistrationRequestHelpers.getPreAuthToken
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.util.UUID

class RegistrationRequestHelpersTest {
    private val preAuthToken = UUID(0, 1)
    private val context = KeyValuePairList(listOf(KeyValuePair(PRE_AUTH_TOKEN, preAuthToken.toString())))
    private val serializedContext = byteArrayOf(0)
    private val signedData = mock<SignedData> {
        on { data } doReturn ByteBuffer.wrap(serializedContext)
    }
    private val registrationRequestDetails = mock<RegistrationRequestDetails> {
        on { registrationContext } doReturn signedData
    }
    private val deserializer = mock<CordaAvroDeserializer<KeyValuePairList>>()

    @Test
    fun `Pre-auth token can be parsed from registration request if present`() {
        whenever(
            deserializer.deserialize(serializedContext)
        ) doReturn context

        val result = assertDoesNotThrow {
            registrationRequestDetails.getPreAuthToken(deserializer)
        }

        Assertions.assertThat(result).isEqualTo(preAuthToken)
    }

    @Test
    fun `Pre-auth token from member context is null if not set`() {
        whenever(
            deserializer.deserialize(serializedContext)
        ) doReturn KeyValuePairList(emptyList())

        val result = assertDoesNotThrow {
            registrationRequestDetails.getPreAuthToken(deserializer)
        }

        Assertions.assertThat(result).isNull()
    }

    @Test
    fun `Pre-auth token throws exception when deserialization fails`() {
        whenever(
            deserializer.deserialize(serializedContext)
        ) doReturn null

        assertThrows<ContextDeserializationException> {
            registrationRequestDetails.getPreAuthToken(deserializer)
        }
    }
}
