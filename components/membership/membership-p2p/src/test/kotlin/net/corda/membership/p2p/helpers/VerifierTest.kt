package net.corda.membership.p2p.helpers

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.SignatureVerificationService
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.exceptions.CryptoSignatureException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
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
        ByteBuffer.wrap(rawSignature)
    )
    private val signatureSpec = CryptoSignatureSpec(SPEC, null, null)
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

        verifier.verify(signature, signatureSpec, data)

        verify(signatureVerificationService).verify(
            eq(data),
            eq(rawSignature),
            same(publicKey),
            argThat<SignatureSpec> {
                this.signatureName == SPEC
            },
        )
    }

    @Test
    fun `verify fails if spec can not be found`() {
        val data = byteArrayOf(44, 1)
        val signature = CryptoSignatureWithKey(
            signature.publicKey,
            signature.bytes
        )
        val badSignatureSpec = CryptoSignatureSpec(null, null, null)

        assertThrows<CordaRuntimeException> {
            verifier.verify(signature, badSignatureSpec, data)
        }
    }

    @Test
    fun `verify fails if signature verification service fails`() {
        val data = byteArrayOf(44, 1)
        whenever(
            signatureVerificationService.verify(
                eq(data),
                eq(rawSignature),
                same(publicKey),
                argThat<SignatureSpec> { this.signatureName == SPEC }
            )
        ).doThrow(CryptoSignatureException("Not verified"))

        assertThrows<CryptoSignatureException> {
            verifier.verify(signature, signatureSpec, data)
        }
    }
}
