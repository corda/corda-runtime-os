package net.corda.membership.p2p.helpers

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.membership.p2p.helpers.Verifier.Companion.SIGNATURE_SPEC
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.cipher.suite.SignatureVerificationService
import net.corda.v5.crypto.SignatureSpec
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import java.nio.ByteBuffer
import java.security.PublicKey

class VerifierTest {
    private companion object {
        const val SPEC = "spec"
    }
    private val rawPublicKey = byteArrayOf(1, 4, 5)
    private val rawSignature = byteArrayOf(6, 7)
    private val signature = CryptoSignatureWithKey(
        ByteBuffer.wrap(rawPublicKey),
        ByteBuffer.wrap(rawSignature),
        KeyValuePairList(
            listOf(
                KeyValuePair(SIGNATURE_SPEC, SPEC)
            )
        )
    )
    private val publicKey = mock<PublicKey>()
    private val keyEncodingService = mock<KeyEncodingService> {
        on { decodePublicKey(rawPublicKey) } doReturn publicKey
    }
    private val signatureVerificationService = mock<SignatureVerificationService>()

    private val verifier = Verifier(
        signatureVerificationService,
        keyEncodingService,
    )

    @Test
    fun `verify call the service with the correct arguments`() {
        val data = byteArrayOf(44, 1)

        verifier.verify(signature, data)

        verify(signatureVerificationService).verify(
            same(publicKey),
            argThat<SignatureSpec> {
                this.signatureName == SPEC
            },
            eq(rawSignature),
            eq(data),
        )
    }

    @Test
    fun `verify fails if spec can not be found`() {
        val data = byteArrayOf(44, 1)
        val badSignature = CryptoSignatureWithKey(
            signature.publicKey,
            signature.bytes,
            KeyValuePairList(emptyList())
        )

        assertThrows<CordaRuntimeException> {
            verifier.verify(badSignature, data)
        }
    }
}
