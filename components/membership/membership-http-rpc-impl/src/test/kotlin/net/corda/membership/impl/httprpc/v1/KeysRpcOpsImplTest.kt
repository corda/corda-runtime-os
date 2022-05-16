package net.corda.membership.impl.httprpc.v1

import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.publicKeyId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.PublicKey

class KeysRpcOpsImplTest {
    private val cryptoOpsClient = mock<CryptoOpsClient>()
    private val keyEncodingService = mock<KeyEncodingService>()

    private val keysOps = KeysRpcOpsImpl(cryptoOpsClient, keyEncodingService)

    @Test
    fun `listKeys return the correct key IDs`() {
        val keys = (1..4).map {
            val idToReturn = "id.$it"
            mock<CryptoSigningKey> {
                on { id } doReturn idToReturn
            }
        }
        whenever(cryptoOpsClient.lookup("id", 0, 500, CryptoKeyOrderBy.NONE, emptyMap())).doReturn(keys)

        val list = keysOps.listKeys("id")

        assertThat(list).containsExactlyInAnyOrder("id.1", "id.2", "id.3", "id.4")
    }

    @Test
    fun `generateKeyPair returns the generated public key ID`() {
        val publicKey = mock<PublicKey>() {
            on { encoded } doReturn byteArrayOf(1, 2, 3)
        }
        whenever(cryptoOpsClient.generateKeyPair("tenantId", "category", "alias")).doReturn(publicKey)

        val id = keysOps.generateKeyPair(holdingIdentityId = "tenantId", alias = "alias", hsmCategory = "category")

        assertThat(id).isEqualTo(publicKey.publicKeyId())
    }

    @Test
    fun `generateKeyPem returns the keys PEMs`() {
        val keyId = "keyId"
        val holdingIdentityId = "holdingIdentityId"
        val publicKeyBytes = "123".toByteArray()
        val key = mock<CryptoSigningKey> {
            on { publicKey } doReturn ByteBuffer.wrap(publicKeyBytes)
        }
        val decodedPublicKey = mock<PublicKey>()
        whenever(cryptoOpsClient.lookup(holdingIdentityId, listOf(keyId))).doReturn(listOf(key))
        whenever(keyEncodingService.decodePublicKey(publicKeyBytes)).doReturn(decodedPublicKey)
        whenever(keyEncodingService.encodeAsString(decodedPublicKey)).doReturn("PEM")

        val pem = keysOps.generateKeyPem(holdingIdentityId, keyId)

        assertThat(pem).isEqualTo("PEM")
    }

    @Test
    fun `generateKeyPem throws Exception when the key is unknwon`() {
        val keyId = "keyId"
        val holdingIdentityId = "holdingIdentityId"
        whenever(cryptoOpsClient.lookup(holdingIdentityId, listOf(keyId))).doReturn(emptyList())

        assertThrows<ResourceNotFoundException> {
            keysOps.generateKeyPem(holdingIdentityId, keyId)
        }
    }

    @Test
    fun `isRunning returns the crypto client status`() {
        whenever(cryptoOpsClient.isRunning).doReturn(true)

        assertThat(keysOps.isRunning).isTrue
    }

    @Test
    fun `start starts the cryptoOpsClient`() {
        keysOps.start()

        verify(cryptoOpsClient).start()
    }

    @Test
    fun `stop stops the cryptoOpsClient`() {
        keysOps.stop()

        verify(cryptoOpsClient).stop()
    }
}
