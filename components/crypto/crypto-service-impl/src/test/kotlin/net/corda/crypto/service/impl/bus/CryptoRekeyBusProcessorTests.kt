package net.corda.crypto.service.impl.bus

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.crypto.core.CryptoService
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.persistence.db.model.WrappingKeyEntity
import net.corda.crypto.softhsm.WrappingRepository
import net.corda.crypto.softhsm.WrappingRepositoryFactory
import net.corda.crypto.softhsm.impl.WrappingRepositoryImpl
import net.corda.crypto.testkit.SecureHashUtils
import net.corda.data.crypto.wire.ops.key.rotation.KeyRotationRequest
import net.corda.data.crypto.wire.ops.key.rotation.KeyType
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager

class CryptoRekeyBusProcessorTests {
    private lateinit var tenantId: String
    private lateinit var cryptoRekeyBusProcessor: CryptoRekeyBusProcessor
    private lateinit var virtualNodeInfoReadService: VirtualNodeInfoReadService
    private lateinit var wrappingRepositoryFactory: WrappingRepositoryFactory
    private lateinit var publisherFactory: PublisherFactory
    private lateinit var publisher: Publisher
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

        val cryptoService: CryptoService = mock<CryptoService> { }

        tenantId = UUID.randomUUID().toString()
        virtualNodeInfoReadService = mock()

        val wrappingRepository: WrappingRepository = mock {
            on { findKey(any()) } doReturn WrappingKeyInfo(0, "", byteArrayOf(), 0, oldKeyAlias)
        }

        wrappingRepositoryFactory = mock {
            on { create(any()) } doReturn wrappingRepository
        }

        publisherFactory = mock()
        publisher = mock()
        whenever(publisherFactory.createPublisher(any(), any())).thenReturn(publisher)

        cryptoRekeyBusProcessor = CryptoRekeyBusProcessor(
            cryptoService, virtualNodeInfoReadService,
            wrappingRepositoryFactory, publisherFactory, config.getConfig(ConfigKeys.MESSAGING_CONFIG)
        )
    }

    @Test
    fun `do a mocked key rotation`() {
        val virtualNodes = getStubVirtualNodes(listOf("Bob"))
        whenever(virtualNodeInfoReadService.getAll()).thenReturn(virtualNodes)

        cryptoRekeyBusProcessor.onNext(listOf(getKafkaRecord("", null)))

        verify(publisher, times(1)).publish(any())
    }

    @Test
    fun `key rotation posts Kafka message for all the keys if limit is not specified`() {
        val virtualNodes = getStubVirtualNodes(listOf("Alice", "Bob", "Charlie"))
        whenever(virtualNodeInfoReadService.getAll()).thenReturn(virtualNodes)

        cryptoRekeyBusProcessor.onNext(listOf(getKafkaRecord("", null)))

        verify(publisher, times(3)).publish(any())
    }

    @Test
    fun `zero limit for key rotation operations is reflected`() {
        val virtualNodes = getStubVirtualNodes(listOf("Alice", "Bob", "Charlie", "David", "Erin"))
        whenever(virtualNodeInfoReadService.getAll()).thenReturn(virtualNodes)

        cryptoRekeyBusProcessor.onNext(listOf(getKafkaRecord("", 0)))

        verify(publisher, never()).publish(any())
    }

    @Test
    fun `limit for key rotation operations is reflected`() {
        val virtualNodes = getStubVirtualNodes(listOf("Alice", "Bob", "Charlie", "David", "Erin"))
        whenever(virtualNodeInfoReadService.getAll()).thenReturn(virtualNodes)

        cryptoRekeyBusProcessor.onNext(listOf(getKafkaRecord("", 2)))

        verify(publisher, times(2)).publish(any())
    }

    /**
     * The test checks the wrapping repo for a tenant and if it finds the key with oldKeyAlias, it publishes Kafka message
     * for that tenant to do the actual key rotation.
     */
    @Test
    fun `key rotation posts new Kafka message only for keys with oldKeyAlias alias in the wrapping repo`() {
        val oldKeyAlias = "Eris"
        val tenantId1 = UUID.randomUUID().toString()
        val tenantId2 = UUID.randomUUID().toString()
        val tenantId3 = UUID.randomUUID().toString()
        val newId = UUID.randomUUID()
        val wrappingKeyInfo = WrappingKeyInfo(
            1, "caesar", SecureHashUtils.randomBytes(), 1, "Enoch"
        )
        val savedWrappingKey = makeWrappingKeyEntity(newId, oldKeyAlias, wrappingKeyInfo)
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
            on { create(virtualNodes[0].holdingIdentity.x500Name.commonName.toString()) } doReturn repo1
            on { create(virtualNodes[1].holdingIdentity.x500Name.commonName.toString()) } doReturn repo2
            on { create(virtualNodes[2].holdingIdentity.x500Name.commonName.toString()) } doReturn repo3
        }

        val cryptoService: CryptoService = mock<CryptoService> { }
        cryptoRekeyBusProcessor = CryptoRekeyBusProcessor(
            cryptoService, virtualNodeInfoReadService,
            wrappingRepositoryFactory, publisherFactory, config.getConfig(ConfigKeys.MESSAGING_CONFIG)
        )

        cryptoRekeyBusProcessor.onNext(listOf(getKafkaRecord(oldKeyAlias, null)))

        verify(publisher, times(2)).publish(any())
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
                on { setMaxResults(any()) } doReturn it
                // Here we set the empty list on a check, if the tenant's wrapping repo contains the key with oldKeyAlias alias.
                on { resultList } doReturn wrappingKeyEntity
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
            virtualNodesInfos.add(
                VirtualNodeInfo(
                    createTestHoldingIdentity("CN=$identity, O=Bob Corp, L=LDN, C=GB", ""),
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

    private fun getKafkaRecord(oldKeyAlias: String, limit: Int?): Record<String, KeyRotationRequest> = Record(
        "TBC",
        UUID.randomUUID().toString(),
        KeyRotationRequest(
            UUID.randomUUID().toString(),
            KeyType.UNMANAGED,
            oldKeyAlias,
            "",
            null,
            tenantId,
            false,
            0,
            limit
        )
    )
}
