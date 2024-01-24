package net.corda.crypto.service.impl.bus

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.crypto.core.CryptoService
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.parseSecureHash
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.persistence.db.model.WrappingKeyEntity
import net.corda.crypto.softhsm.WrappingRepository
import net.corda.crypto.softhsm.WrappingRepositoryFactory
import net.corda.crypto.softhsm.impl.WrappingRepositoryImpl
import net.corda.crypto.testkit.SecureHashUtils
import net.corda.data.crypto.wire.ops.key.rotation.KeyRotationRequest
import net.corda.data.crypto.wire.ops.key.rotation.KeyType
import net.corda.data.crypto.wire.ops.key.status.UnmanagedKeyStatus
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.ConfigKeys
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import kotlin.streams.asStream


class CryptoRekeyBusProcessorTests {
    private lateinit var tenantId: String
    private val tenantId1 = ShortHash.of(parseSecureHash("SHA-256:ABC12345678911111111111111")).toString()
    private val tenantId2 = ShortHash.of(parseSecureHash("SHA-256:BCC12345678911111111111111")).toString()
    private val tenantId3 = ShortHash.of(parseSecureHash("SHA-256:CBC12345678911111111111111")).toString()
    private lateinit var cryptoRekeyBusProcessor: CryptoRekeyBusProcessor
    private lateinit var virtualNodeInfoReadService: VirtualNodeInfoReadService
    private lateinit var wrappingRepositoryFactory: WrappingRepositoryFactory
    private lateinit var rewrapPublishCapture: KArgumentCaptor<List<Record<*, *>>>
    private lateinit var cryptoService: CryptoService
    private lateinit var rewrapPublisher: Publisher
    private lateinit var cordaAvroSerializationFactory: CordaAvroSerializationFactory
    private lateinit var config: Map<String, SmartConfig>
    private val oldKeyAlias = "oldKeyAlias"

    @BeforeEach
    fun setup() {
        val configEvent = ConfigChangedEvent(
            setOf(ConfigKeys.MESSAGING_CONFIG),
            mapOf(
                ConfigKeys.MESSAGING_CONFIG to
                    SmartConfigFactory.createWithoutSecurityServices().create(
                        createMessagingConfig()
                    )
            )
        )
        config = configEvent.config

        cryptoService = mock<CryptoService> { }

        tenantId = UUID.randomUUID().toString()
        virtualNodeInfoReadService = mock()

        val wrappingRepository: WrappingRepository = mock {
            on { findKeysWrappedByParentKey(any()) } doReturn listOf(
                WrappingKeyInfo(
                    0,
                    "",
                    byteArrayOf(),
                    0,
                    oldKeyAlias,
                    "alias1"
                )
            )
        }

        wrappingRepositoryFactory = mock {
            on { create(any()) } doReturn wrappingRepository
        }
        rewrapPublishCapture = argumentCaptor()
        rewrapPublisher = mock {
            on { publish(rewrapPublishCapture.capture()) } doReturn emptyList()
        }

        val serializer = mock<CordaAvroSerializer<UnmanagedKeyStatus>> {
            on { serialize(any()) } doReturn byteArrayOf(42)
        }
        cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
            on { createAvroSerializer<UnmanagedKeyStatus>() } doReturn serializer
        }

        cryptoRekeyBusProcessor = CryptoRekeyBusProcessor(
            cryptoService, virtualNodeInfoReadService,
            wrappingRepositoryFactory, rewrapPublisher,
            mock(),
            cordaAvroSerializationFactory
        )
    }

    @Test
    fun `key rotation re-wraps all the keys`() {
        val virtualNodes = getStubVirtualNodes(listOf(tenantId1, tenantId2, tenantId3))
        whenever(virtualNodeInfoReadService.getAll()).thenReturn(virtualNodes)

        cryptoRekeyBusProcessor.onNext(listOf(getKafkaRecord("")))

        verify(rewrapPublisher, times(1)).publish(any())
        assertThat(rewrapPublishCapture.allValues).hasSize(1)
        assertThat(rewrapPublishCapture.firstValue).hasSize(4) // 4 since 3 tenants plus cluster
    }

    /**
     * The test checks that if the wrapping repo of the particular tenant contains the wrapping key info with the correct
     * oldKeyAlias that we want to re-wrap, it would run the rewrapWrappingKey function
     *
     * TenantId1 and tenantId3 only would return the wrapping key info containing the oldKeyAlias.
     * Other tenants return null, therefore no rewrapWrappingKey function is called.
     */
    @Test
    fun `key rotation re-wraps only those keys where oldKeyAlias alias is in the wrapping repo for the tenant`() {
        val oldKeyAlias = "Eris"
        val newId = UUID.randomUUID()
        val wrappingKeyInfo = WrappingKeyInfo(
            1, "caesar", SecureHashUtils.randomBytes(), 1, "Eris", "alias1"
        )
        val savedWrappingKey = makeWrappingKeyEntity(newId, oldKeyAlias, wrappingKeyInfo)
        // Mock the entity manager's functions used by findKeysWrappedByAlias
        val em1 = createEntityManager(listOf(savedWrappingKey))
        val repo1 = createWrappingRepo(em1, tenantId1)

        // We pass empty entity manager, so the request for a key with oldKeyAlias will return null
        val em2 = createEntityManager(listOf())

        // Repo2 returns empty list when requested if the wrapping repo contains the key with oldKeyAlias.
        val repo2 = createWrappingRepo(em2, tenantId2)
        // Repo3 uses the entity manager for the repo1 as we want to get return some value.
        val repo3 = createWrappingRepo(em1, tenantId3)

        val virtualNodes = getStubVirtualNodes(listOf(tenantId1, tenantId2, tenantId3))
        whenever(virtualNodeInfoReadService.getAll()).thenReturn(virtualNodes)

        val wrappingRepositoryFactory = mock<WrappingRepositoryFactory> {
            on { create(tenantId1) } doReturn repo1
            on { create(tenantId2) } doReturn repo2
            on { create(tenantId3) } doReturn repo3
            on { create(CryptoTenants.CRYPTO) } doReturn repo2
        }

        cryptoRekeyBusProcessor = CryptoRekeyBusProcessor(
            cryptoService,
            virtualNodeInfoReadService,
            wrappingRepositoryFactory,
            rewrapPublisher,
            mock(),
            cordaAvroSerializationFactory,
        )

        cryptoRekeyBusProcessor.onNext(listOf(getKafkaRecord(oldKeyAlias)))

        verify(rewrapPublisher, times(1)).publish(any())
        assertThat(rewrapPublishCapture.allValues).hasSize(1)
        assertThat(rewrapPublishCapture.firstValue).hasSize(0)
    }

    private fun makeWrappingKeyEntity(
        newId: UUID,
        alias: String,
        wrappingKeyInfo: WrappingKeyInfo,
    ): WrappingKeyEntity = WrappingKeyEntity(
        newId,
        alias,
        wrappingKeyInfo.generation,
        mock(),
        wrappingKeyInfo.encodingVersion,
        wrappingKeyInfo.algorithmName,
        wrappingKeyInfo.keyMaterial,
        mock(),
        false,
        wrappingKeyInfo.parentKeyAlias
    )

    private fun createEntityManager(wrappingKeyEntity: List<WrappingKeyEntity>): EntityManager = mock<EntityManager> {
        on { createQuery(any(), eq(WrappingKeyEntity::class.java)) } doAnswer {
            mock {
                on { setParameter(any<String>(), any()) } doReturn it
                // Here we set the empty list on a check, if the tenant's wrapping repo contains the key with oldKeyAlias alias.
                on { resultStream } doReturn wrappingKeyEntity.asSequence().asStream()
            }
        }
    }

    private fun createWrappingRepo(entityManager: EntityManager, tenantId: String): WrappingRepository =
        WrappingRepositoryImpl(
            mock { on { createEntityManager() } doReturn entityManager },
            tenantId
        )

    private fun createMessagingConfig(): SmartConfig =
        SmartConfigFactory.createWithoutSecurityServices().create(ConfigFactory.empty())
            .withValue(
                ConfigKeys.MESSAGING_CONFIG, ConfigValueFactory.fromAnyRef("random")
            )

    private fun getStubVirtualNodes(identities: List<String>): List<VirtualNodeInfo> {
        val virtualNodesInfos = mutableListOf<VirtualNodeInfo>()
        identities.forEach { identity ->
            val holdingIdentity = mock<HoldingIdentity> {
                on { shortHash } doReturn ShortHash.of(identity)
            }

            virtualNodesInfos.add(
                VirtualNodeInfo(
                    holdingIdentity,
                    CpiIdentifier(
                        "", "",
                        SecureHashImpl("", "bytes".toByteArray())
                    ),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    version = 0,
                    timestamp = Instant.now(),
                )
            )
        }
        return virtualNodesInfos
    }

    private fun getKafkaRecord(oldKeyAlias: String): Record<String, KeyRotationRequest> = Record(
        "TBC",
        UUID.randomUUID().toString(),
        KeyRotationRequest(
            UUID.randomUUID().toString(),
            KeyType.UNMANAGED,
            oldKeyAlias,
            "",
            tenantId,
        )
    )
}
