package net.corda.membership.impl.httprpc.v1

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.CryptoConsts.Categories.SESSION_INIT
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.ALIAS_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CATEGORY_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CREATED_AFTER_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CREATED_BEFORE_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.MASTER_KEY_ALIAS_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.SCHEME_CODE_NAME_FILTER
import net.corda.crypto.core.CryptoTenants.P2P
import net.corda.crypto.core.InvalidParamsException
import net.corda.crypto.core.KeyAlreadyExistsException
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.httprpc.exception.InvalidInputDataException
import net.corda.httprpc.exception.ResourceAlreadyExistsException
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.httprpc.exception.ServiceUnavailableException
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.membership.httprpc.v1.types.response.KeyMetaData
import net.corda.membership.httprpc.v1.types.response.KeyPairIdentifier
import net.corda.messaging.api.exception.CordaRPCAPIPartitionException
import net.corda.v5.crypto.publicKeyId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.PublicKey
import java.time.Instant

class KeysRestResourceImplTest {
    private companion object {
        const val ALIAS = "alias"
        const val SCHEME = "scheme"
        const val CATEGORY = "CATEGORY"
        const val TENANT_ID = "tenantId"
        const val EXCEPTION_MSG = "exception happened"
    }

    private val cryptoOpsClient = mock<CryptoOpsClient>()
    private val keyEncodingService = mock<KeyEncodingService>()
    private val coordinator = mock<LifecycleCoordinator>()
    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }

    private val keysOps = KeysRestResourceImpl(cryptoOpsClient, keyEncodingService, lifecycleCoordinatorFactory)

    @Nested
    inner class BasicApiTests {
        @Test
        fun `listKeys return the correct key IDs`() {
            val keys = (1..4).map {
                val idToReturn = "id.$it"
                val aliasToReturn = "alias-$it"
                val categoryToReturn = "CATEGORY:$it"
                val scheme = "scheme($it)"
                val createdTimestamp = Instant.ofEpochMilli(it * 1000L)
                val mka = "master key alias $it"
                mock<CryptoSigningKey> {
                    on { id } doReturn idToReturn
                    on { alias } doReturn aliasToReturn
                    on { category } doReturn categoryToReturn
                    on { schemeCodeName } doReturn scheme
                    on { created } doReturn createdTimestamp
                    on { masterKeyAlias } doReturn mka
                }
            }
            whenever(cryptoOpsClient.lookup(TENANT_ID, 4, 400, CryptoKeyOrderBy.ALIAS, emptyMap())).doReturn(keys)

            val list = keysOps.listKeys(
                tenantId = TENANT_ID,
                skip = 4,
                take = 400,
                orderBy = ALIAS,
                category = null,
                alias = null,
                masterKeyAlias = null,
                createdAfter = null,
                createdBefore = null,
                schemeCodeName = null,
                ids = null
            )

            val expectedKeys = (1..4).map {
                KeyMetaData(
                    keyId = "id.$it",
                    alias = "alias-$it",
                    hsmCategory = "CATEGORY:$it",
                    scheme = "scheme($it)",
                    created = Instant.ofEpochMilli(it * 1000L),
                    masterKeyAlias = "master key alias $it"
                )
            }.associateBy { it.keyId }

            assertThat(list).containsAllEntriesOf(expectedKeys)
        }

        @Test
        fun `listKeys with IDs calls the correct function`() {
            val keys = (1..3).map {
                val idToReturn = "id.$it"
                val aliasToReturn = "alias-$it"
                val categoryToReturn = "CATEGORY:$it"
                val scheme = "scheme($it)"
                val createdTimestamp = Instant.ofEpochMilli(it * 1000L)
                val mka = "master key alias $it"
                mock<CryptoSigningKey> {
                    on { id } doReturn idToReturn
                    on { alias } doReturn aliasToReturn
                    on { category } doReturn categoryToReturn
                    on { schemeCodeName } doReturn scheme
                    on { created } doReturn createdTimestamp
                    on { masterKeyAlias } doReturn mka
                }
            }
            whenever(cryptoOpsClient.lookup(any(), any())).doReturn(keys)

            val list = keysOps.listKeys(
                tenantId = TENANT_ID,
                skip = 4,
                take = 400,
                orderBy = ALIAS,
                category = null,
                alias = null,
                masterKeyAlias = null,
                createdAfter = null,
                createdBefore = null,
                schemeCodeName = null,
                ids = listOf("a", "b"),
            )

            val expectedKeys = (1..3).map {
                KeyMetaData(
                    keyId = "id.$it",
                    alias = "alias-$it",
                    hsmCategory = "CATEGORY:$it",
                    scheme = "scheme($it)",
                    created = Instant.ofEpochMilli(it * 1000L),
                    masterKeyAlias = "master key alias $it"
                )
            }.associateBy { it.keyId }
            assertThat(list).containsAllEntriesOf(expectedKeys)
        }

        @Test
        fun `listKeys with invalid order by will throw an exception`() {
            assertThrows<ResourceNotFoundException> {
                keysOps.listKeys(
                    tenantId = TENANT_ID,
                    skip = 4,
                    take = 400,
                    orderBy = "nopp",
                    category = null,
                    alias = null,
                    masterKeyAlias = null,
                    createdAfter = null,
                    createdBefore = null,
                    schemeCodeName = null,
                    ids = emptyList(),
                )
            }
        }

        @Test
        fun `listKeys will send the correct filter values`() {
            val filterMap = argumentCaptor<Map<String, String>>()
            whenever(cryptoOpsClient.lookup(any(), any(), any(), any(), filterMap.capture())).doReturn(emptyList())

            keysOps.listKeys(
                tenantId = TENANT_ID,
                skip = 4,
                take = 400,
                orderBy = ALIAS,
                category = "c1",
                alias = "a1",
                masterKeyAlias = "mka1",
                createdAfter = "1970-01-01T01:00:00.000Z",
                createdBefore = "1980-01-01T01:00:00.000Z",
                schemeCodeName = "sc1",
                ids = emptyList(),
            )

            assertThat(filterMap.firstValue)
                .containsEntry(CATEGORY_FILTER, "C1")
                .containsEntry(SCHEME_CODE_NAME_FILTER, "sc1")
                .containsEntry(ALIAS_FILTER, "a1")
                .containsEntry(MASTER_KEY_ALIAS_FILTER, "mka1")
                .containsEntry(CREATED_BEFORE_FILTER, "1980-01-01T01:00:00.000Z")
                .containsEntry(CREATED_AFTER_FILTER, "1970-01-01T01:00:00.000Z")
        }

        @Test
        fun `listKeys will throw an exception for invalid before time`() {
            assertThrows<ResourceNotFoundException> {
                keysOps.listKeys(
                    tenantId = TENANT_ID,
                    skip = 4,
                    take = 400,
                    orderBy = ALIAS,
                    category = null,
                    alias = null,
                    masterKeyAlias = null,
                    createdAfter = null,
                    createdBefore = "nop",
                    schemeCodeName = null,
                    ids = null,
                )
            }
        }

        @Test
        fun `listKeys will throw an exception for invalid after time`() {
            assertThrows<ResourceNotFoundException> {
                keysOps.listKeys(
                    tenantId = TENANT_ID,
                    skip = 4,
                    take = 400,
                    orderBy = ALIAS,
                    category = null,
                    alias = null,
                    masterKeyAlias = null,
                    createdAfter = "nop",
                    createdBefore = null,
                    schemeCodeName = null,
                    ids = null,
                )
            }
        }

        @Test
        fun `listKeys will throw ServiceUnavailableException when repartition event happens while trying to lookup keys for tenant`() {
            whenever(cryptoOpsClient.lookup(any(), any())).doThrow(CordaRPCAPIPartitionException("repartition event"))

            val details = assertThrows<ServiceUnavailableException> {
                keysOps.listKeys(
                    tenantId = TENANT_ID,
                    skip = 4,
                    take = 400,
                    orderBy = ALIAS,
                    category = null,
                    alias = null,
                    masterKeyAlias = null,
                    createdAfter = null,
                    createdBefore = null,
                    schemeCodeName = null,
                    ids = listOf("key1"),
                )
            }

            assertThat(details.message).isEqualTo("Could not lookup keys for tenant $TENANT_ID: Repartition Event!")
        }

        @Test
        @Suppress("MaxLineLength")
        fun `listKeys will throw ServiceUnavailableException when repartition event happens while trying to lookup filtered keys for tenant`() {
            whenever(cryptoOpsClient.lookup(TENANT_ID, 4, 400, CryptoKeyOrderBy.ALIAS, emptyMap()))
                .doThrow(CordaRPCAPIPartitionException("repartition event"))

            val details = assertThrows<ServiceUnavailableException> {
                keysOps.listKeys(
                    tenantId = TENANT_ID,
                    skip = 4,
                    take = 400,
                    orderBy = ALIAS,
                    category = null,
                    alias = null,
                    masterKeyAlias = null,
                    createdAfter = null,
                    createdBefore = null,
                    schemeCodeName = null,
                    ids = null
                )
            }

            assertThat(details.message).isEqualTo("Could not lookup keys for tenant $TENANT_ID: Repartition Event!")
        }

        @Test
        fun `generateKeyPair returns the generated public key ID`() {
            val publicKey = mock<PublicKey> {
                on { encoded } doReturn byteArrayOf(1, 2, 3)
            }
            whenever(cryptoOpsClient.generateKeyPair(TENANT_ID, CATEGORY.uppercase(), ALIAS, SCHEME)).doReturn(publicKey)

            val id = keysOps.generateKeyPair(tenantId = TENANT_ID, alias = ALIAS, hsmCategory = CATEGORY, scheme = SCHEME)

            assertThat(id).isEqualTo(KeyPairIdentifier(publicKey.publicKeyId()))
        }

        @Test
        fun `generateKeyPair throws exception for empty alias`() {
            val publicKey = mock<PublicKey> {
                on { encoded } doReturn byteArrayOf(1, 2, 3)
            }
            whenever(cryptoOpsClient.generateKeyPair(TENANT_ID, CATEGORY.uppercase(), ALIAS, SCHEME)).doReturn(publicKey)

            val exceptionDetails = assertThrows<InvalidInputDataException> {
                keysOps.generateKeyPair(tenantId = TENANT_ID, alias = "", hsmCategory = CATEGORY, scheme = SCHEME)
            }.details
            assertThat(exceptionDetails).containsKey(ALIAS)
        }

        @Test
        fun `generateKeyPair throws exception for duplicate key`() {
            whenever(cryptoOpsClient.generateKeyPair(any(), any(), any(), any(), any<Map<String, String>>()))
                .doThrow(KeyAlreadyExistsException(""))

            assertThrows<ResourceAlreadyExistsException> {
                keysOps.generateKeyPair(
                    tenantId = TENANT_ID,
                    alias = ALIAS,
                    hsmCategory = CATEGORY,
                    scheme = SCHEME,
                )
            }
        }

        @Test
        fun `generateKeyPair throws InvalidInputDataException when the category is session init and the tenant is not a virtual node`() {
            assertThrows<InvalidInputDataException> {
                keysOps.generateKeyPair(tenantId = P2P, alias = ALIAS, hsmCategory = SESSION_INIT, scheme = SCHEME)
            }
        }

        @Test
        fun `generateKeyPair will not throw an exception when the category is session init and the tenant is a virtual node`() {
            val tenantId = "123123123123"
            val publicKey = mock<PublicKey> {
                on { encoded } doReturn byteArrayOf(1, 2, 3)
            }
            whenever(cryptoOpsClient.generateKeyPair(tenantId, SESSION_INIT, ALIAS, SCHEME)).doReturn(publicKey)

            keysOps.generateKeyPair(
                tenantId = tenantId,
                alias = ALIAS,
                hsmCategory = SESSION_INIT,
                scheme = SCHEME
            )
        }

        @Test
        fun `generateKeyPair throws ServiceUnavailableException when repartition event happens while trying to generate the keyPair`() {
            whenever(cryptoOpsClient.generateKeyPair(TENANT_ID, CATEGORY.uppercase(), ALIAS, SCHEME))
                .doThrow(CordaRPCAPIPartitionException("repartition event"))

            val details = assertThrows<ServiceUnavailableException> {
                keysOps.generateKeyPair(tenantId = TENANT_ID, alias = ALIAS, hsmCategory = CATEGORY, scheme = SCHEME)
            }

            assertThat(details.message).isEqualTo("Could not generate key pair for tenant $TENANT_ID: Repartition Event!")
        }

        @Test
        fun `generateKeyPem returns the keys PEMs`() {
            val keyId = "keyId"
            val holdingIdentityShortHash = "holdingIdentityShortHash"
            val publicKeyBytes = "123".toByteArray()
            val key = mock<CryptoSigningKey> {
                on { publicKey } doReturn ByteBuffer.wrap(publicKeyBytes)
            }
            val decodedPublicKey = mock<PublicKey>()
            whenever(cryptoOpsClient.lookup(holdingIdentityShortHash, listOf(keyId))).doReturn(listOf(key))
            whenever(keyEncodingService.decodePublicKey(publicKeyBytes)).doReturn(decodedPublicKey)
            whenever(keyEncodingService.encodeAsString(decodedPublicKey)).doReturn("PEM")

            val pem = keysOps.generateKeyPem(holdingIdentityShortHash, keyId)

            assertThat(pem).isEqualTo("PEM")
        }

        @Test
        fun `generateKeyPem throws Exception when the key is unknwon`() {
            val keyId = "keyId"
            val holdingIdentityShortHash = "holdingIdentityShortHash"
            whenever(cryptoOpsClient.lookup(holdingIdentityShortHash, listOf(keyId))).doReturn(emptyList())

            assertThrows<ResourceNotFoundException> {
                keysOps.generateKeyPem(holdingIdentityShortHash, keyId)
            }
        }

        @Test
        fun `generateKeyPem throws ServiceUnavailableException when repartition event happens while trying to lookup keys for tenant`() {
            whenever(cryptoOpsClient.lookup(any(), any()))
                .doThrow(CordaRPCAPIPartitionException("repartition event"))

            val details = assertThrows<ServiceUnavailableException> {
                keysOps.generateKeyPem(TENANT_ID, "keyId")
            }

            assertThat(details.message).isEqualTo("Could not lookup keys for tenant $TENANT_ID: Repartition Event!")
        }

        @Test
        fun `listSchemes return list of schemes`() {
            whenever(cryptoOpsClient.getSupportedSchemes(TENANT_ID, CATEGORY.uppercase())).doReturn(listOf("one", "two"))

            val schemes = keysOps.listSchemes(TENANT_ID, CATEGORY)

            assertThat(schemes).containsExactlyInAnyOrder("one", "two")
        }

        @Test
        fun `listSchemes throws ServiceUnavailableException when repartition event happens while trying to retrieve schemes`() {
            whenever(cryptoOpsClient.getSupportedSchemes(any(), any()))
                .doThrow(CordaRPCAPIPartitionException("repartition event"))

            val details = assertThrows<ServiceUnavailableException> {
                keysOps.listSchemes(TENANT_ID, CATEGORY)
            }

            assertThat(details.message).isEqualTo("Could not list supported schemes for tenant $TENANT_ID: Repartition Event!")
        }


        @Test
        fun `InvalidParamsException is mapped to InvalidInputException`() {
            whenever(cryptoOpsClient.generateKeyPair(TENANT_ID, CATEGORY, ALIAS, SCHEME))
                .thenThrow(InvalidParamsException(EXCEPTION_MSG))
            val ex = assertThrows<InvalidInputDataException> {
                keysOps.generateKeyPair(TENANT_ID, ALIAS, CATEGORY, SCHEME)
            }
            assertThat(ex.message).contains(EXCEPTION_MSG)
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
        fun `start starts the coordinator`() {
            keysOps.start()

            verify(coordinator).start()
        }

        @Test
        fun `stop stops the coordinator`() {
            keysOps.stop()

            verify(coordinator).stop()
        }

        @Test
        fun `UP event will set the status to up`() {
            handler.firstValue.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), mock())

            verify(coordinator).updateStatus(LifecycleStatus.UP, "Dependencies are UP")
        }

        @Test
        fun `DOWN event will set the status to down`() {
            handler.firstValue.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.DOWN), mock())

            verify(coordinator).updateStatus(LifecycleStatus.DOWN, "Dependencies are DOWN")
        }
    }
}
