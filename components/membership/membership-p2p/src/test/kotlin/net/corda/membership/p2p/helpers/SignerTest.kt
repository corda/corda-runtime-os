package net.corda.membership.p2p.helpers

import net.corda.crypto.cipher.suite.publicKeyId
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.ShortHash
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.membership.p2p.helpers.Verifier.Companion.SIGNATURE_SPEC
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.KeySchemeCodes.RSA_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey

class SignerTest {
    private val tenantId = "tenantId"
    private val publicKey = mock<PublicKey> {
        on { encoded } doReturn "pk".toByteArray()
    }
    private val cryptoOpsClient = mock<CryptoOpsClient>()

    private val signer = Signer(tenantId, publicKey, cryptoOpsClient)

    @Test
    fun `sign return the signature`() {
        val data = byteArrayOf(1, 2, 3)
        val key = mock<CryptoSigningKey> {
            on { schemeCodeName } doReturn RSA_CODE_NAME
        }
        whenever(cryptoOpsClient.lookupKeysByIds(tenantId, listOf(ShortHash.of(publicKey.publicKeyId())))).doReturn(listOf(key))
        val signature = mock<DigitalSignature.WithKey>()
        whenever(
            cryptoOpsClient.sign(
                tenantId = tenantId,
                publicKey = publicKey,
                data = data,
                signatureSpec = SignatureSpec.RSA_SHA512,
                context = mapOf(SIGNATURE_SPEC to SignatureSpec.RSA_SHA512.signatureName)
            )
        ).doReturn(signature)

        assertThat(signer.sign(data)).isEqualTo(signature)
    }

    @Test
    fun `sign fail if spec can not be found`() {
        val data = byteArrayOf(1, 2, 3)
        val key = mock<CryptoSigningKey> {
            on { schemeCodeName } doReturn "NOP"
        }
        whenever(cryptoOpsClient.lookupKeysByIds(tenantId, listOf(ShortHash.of(publicKey.publicKeyId())))).doReturn(listOf(key))

        assertThrows<CordaRuntimeException> {
            signer.sign(data)
        }
    }

    @Test
    fun `sign fail if key can not be found`() {
        val data = byteArrayOf(1, 2, 3)
        whenever(cryptoOpsClient.lookupKeysByIds(tenantId, listOf(ShortHash.of(publicKey.publicKeyId())))).doReturn(emptyList())

        assertThrows<CordaRuntimeException> {
            signer.sign(data)
        }
    }
}
