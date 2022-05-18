package net.corda.membership.impl.httprpc.v1

import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.membership.httprpc.v1.types.response.KeyMetaData
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.publicKeyId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.PublicKey

class KeysRpcOpsImplTest {
    private val cryptoOpsClient = mock<CryptoOpsClient>()
    private val keyEncodingService = mock<KeyEncodingService>()
    private val coordinator = mock<LifecycleCoordinator>()
    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }

    private val keysOps = KeysRpcOpsImpl(cryptoOpsClient, keyEncodingService, lifecycleCoordinatorFactory)

    @Nested
    inner class BasicApiTests {
        @Test
        fun `listKeys return the correct key IDs`() {
            val keys = (1..4).map {
                val idToReturn = "id.$it"
                val aliasToReturn = "alias-$it"
                val categoryToReturn = "category:$it"
                val scheme = "scheme($it)"
                mock<CryptoSigningKey> {
                    on { id } doReturn idToReturn
                    on { alias } doReturn aliasToReturn
                    on { category } doReturn categoryToReturn
                    on { schemeCodeName } doReturn scheme
                }
            }
            whenever(cryptoOpsClient.lookup("id", 0, 500, CryptoKeyOrderBy.NONE, emptyMap())).doReturn(keys)

            val list = keysOps.listKeys("id")

            assertThat(list)
                .containsEntry(
                    "id.1", KeyMetaData("id.1", "alias-1", "category:1", "scheme(1)")
                )
                .containsEntry(
                    "id.2", KeyMetaData("id.2", "alias-2", "category:2", "scheme(2)")
                )
                .containsEntry(
                    "id.3", KeyMetaData("id.3", "alias-3", "category:3", "scheme(3)")
                )
                .containsEntry(
                    "id.4", KeyMetaData("id.4", "alias-4", "category:4", "scheme(4)")
                )
        }

        @Test
        fun `generateKeyPair returns the generated public key ID`() {
            val publicKey = mock<PublicKey> {
                on { encoded } doReturn byteArrayOf(1, 2, 3)
            }
            whenever(cryptoOpsClient.generateKeyPair("tenantId", "category", "alias", "scheme")).doReturn(publicKey)

            val id = keysOps.generateKeyPair(holdingIdentityId = "tenantId", alias = "alias", hsmCategory = "category", scheme = "scheme")

            assertThat(id).isEqualTo(publicKey.publicKeyId())
        }

        @Test
        fun `generateKeyPair without a scheme use the first scheme`() {
            val publicKey = mock<PublicKey> {
                on { encoded } doReturn byteArrayOf(1, 2, 3)
            }
            whenever(cryptoOpsClient.getSupportedSchemes("tenantId", "category")).doReturn(listOf("sc1", "sc2"))
            whenever(cryptoOpsClient.generateKeyPair(any(), any(), any(), any(), any<Map<String, String>>())).doReturn(publicKey)

            keysOps.generateKeyPair(holdingIdentityId = "tenantId", alias = "alias", hsmCategory = "category", scheme = null)

            verify(cryptoOpsClient).generateKeyPair("tenantId", "category", "alias", "sc1")
        }

        @Test
        fun `generateKeyPair without a scheme throw an exception if there are no schemes`() {
            whenever(cryptoOpsClient.getSupportedSchemes("tenantId", "category")).doReturn(emptyList())

            assertThrows<ResourceNotFoundException> {
                keysOps.generateKeyPair(holdingIdentityId = "tenantId", alias = "alias", hsmCategory = "category", scheme = null)
            }
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
        fun `listSchemes return list of schemes`() {
            whenever(cryptoOpsClient.getSupportedSchemes("id", "category")).doReturn(listOf("one", "two"))

            val schemes = keysOps.listSchemes("id", "category")

            assertThat(schemes).containsExactlyInAnyOrder("one", "two")
        }
    }

    @Nested
    inner class LifeCycleTests {
        @Test
        fun `isRunning returns the coordinator status`() {
            whenever(coordinator.status).doReturn(LifecycleStatus.UP)

            assertThat(keysOps.isRunning).isTrue
        }

        @Test
        fun `start starts the cryptoOpsClient and coordinator`() {
            keysOps.start()

            verify(cryptoOpsClient).start()
            verify(coordinator).start()
        }

        @Test
        fun `stop stops the cryptoOpsClient and coordinator`() {
            keysOps.stop()

            verify(cryptoOpsClient).stop()
            verify(coordinator).stop()
        }

        @Test
        fun `UP event will set the status to up if not up`() {
            whenever(coordinator.status).doReturn(LifecycleStatus.DOWN)

            handler.firstValue.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), mock())

            verify(coordinator).updateStatus(LifecycleStatus.UP, "Dependencies are UP")
        }

        @Test
        fun `UP event will not set the status to up if already up`() {
            whenever(coordinator.status).doReturn(LifecycleStatus.UP)

            handler.firstValue.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), mock())

            verify(coordinator, never()).updateStatus(any(), any())
        }

        @Test
        fun `DOWN event will set the status to up if not DOWN`() {
            whenever(coordinator.status).doReturn(LifecycleStatus.UP)

            handler.firstValue.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.DOWN), mock())

            verify(coordinator).updateStatus(LifecycleStatus.DOWN, "Dependencies are DOWN")
        }

        @Test
        fun `DOWN event will not set the status to down if already down`() {
            whenever(coordinator.status).doReturn(LifecycleStatus.DOWN)

            handler.firstValue.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.DOWN), mock())

            verify(coordinator, never()).updateStatus(any(), any())
        }
    }
}
