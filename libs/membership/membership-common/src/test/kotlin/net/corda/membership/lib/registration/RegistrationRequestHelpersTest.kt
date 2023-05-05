package net.corda.membership.lib.registration

import net.corda.data.CordaAvroDeserializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.SignedData
import net.corda.data.membership.common.RegistrationStatus
import net.corda.membership.lib.registration.RegistrationRequestHelpers.getPreAuthToken
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.util.UUID

class RegistrationRequestHelpersTest {
    val serialisedRegistrationContext = "reg-con".toByteArray()
    val keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList> = mock()
    val registrationRequest = RegistrationRequest(
        RegistrationStatus.APPROVED,
        UUID(1, 9).toString(),
        HoldingIdentity(MemberX500Name.parse("O=Alice, L=London, C=GB"), "bar"),
        SignedData(
            ByteBuffer.wrap("mem-con".toByteArray()),
            CryptoSignatureWithKey(
                ByteBuffer.wrap(byteArrayOf(0)),
                ByteBuffer.wrap(byteArrayOf(1))
            ),
            CryptoSignatureSpec()
        ),
        SignedData(
            ByteBuffer.wrap(serialisedRegistrationContext),
            CryptoSignatureWithKey(
                ByteBuffer.wrap(byteArrayOf(0)),
                ByteBuffer.wrap(byteArrayOf(1))
            ),
            CryptoSignatureSpec()
        ),
        1L
    )

    @Test
    fun `Pre-auth token can be parsed from registration request if present`() {
        val preAuthToken = UUID(0, 1)
        val regCon = KeyValuePairList(listOf(KeyValuePair(PRE_AUTH_TOKEN, preAuthToken.toString())))
        whenever(
            keyValuePairListDeserializer.deserialize(serialisedRegistrationContext)
        ) doReturn regCon

        val result = assertDoesNotThrow {
            registrationRequest.getPreAuthToken(keyValuePairListDeserializer)
        }

        Assertions.assertThat(result).isEqualTo(preAuthToken)
    }

    @Test
    fun `Pre-auth token from member context is null if not set`() {
        val regCon = KeyValuePairList(emptyList())
        whenever(
            keyValuePairListDeserializer.deserialize(serialisedRegistrationContext)
        ) doReturn regCon

        val result = assertDoesNotThrow {
            registrationRequest.getPreAuthToken(keyValuePairListDeserializer)
        }

        Assertions.assertThat(result).isNull()
    }
}