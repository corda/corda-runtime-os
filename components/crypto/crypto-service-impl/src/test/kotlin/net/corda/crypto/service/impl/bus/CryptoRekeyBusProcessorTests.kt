package net.corda.crypto.service.impl.bus

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.crypto.core.CryptoService
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.KeyRotationKeyType
import net.corda.crypto.core.KeyRotationMetadataValues
import net.corda.crypto.core.KeyRotationRecordType
import net.corda.crypto.core.KeyRotationStatus
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.getKeyRotationStatusRecordKey
import net.corda.crypto.core.parseSecureHash
import net.corda.crypto.persistence.SigningKeyMaterialInfo
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.persistence.db.model.WrappingKeyEntity
import net.corda.crypto.softhsm.SigningRepository
import net.corda.crypto.softhsm.SigningRepositoryFactory
import net.corda.crypto.softhsm.WrappingRepository
import net.corda.crypto.softhsm.WrappingRepositoryFactory
import net.corda.crypto.softhsm.impl.WrappingRepositoryImpl
import net.corda.crypto.testkit.SecureHashUtils
import net.corda.data.crypto.wire.ops.key.rotation.IndividualKeyRotationRequest
import net.corda.data.crypto.wire.ops.key.rotation.KeyRotationRequest
import net.corda.data.crypto.wire.ops.key.rotation.KeyType
import net.corda.data.crypto.wire.ops.key.status.ManagedKeyStatus
import net.corda.data.crypto.wire.ops.key.status.UnmanagedKeyStatus
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.statemanager.api.STATE_TYPE
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
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
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager


class CryptoRekeyBusProcessorTests {
    private val tenantId1 = ShortHash.of(parseSecureHash("SHA-256:ABC12345678911111111111111")).toString()
    private val tenantId2 = ShortHash.of(parseSecureHash("SHA-256:BCC12345678911111111111111")).toString()
    private val tenantId3 = ShortHash.of(parseSecureHash("SHA-256:CBC12345678911111111111111")).toString()
    private lateinit var cryptoRekeyBusProcessor: CryptoRekeyBusProcessor
    private lateinit var virtualNodeInfoReadService: VirtualNodeInfoReadService
    private lateinit var wrappingRepositoryFactory: WrappingRepositoryFactory
    private lateinit var signingRepositoryFactory: SigningRepositoryFactory
    private lateinit var rewrapPublishCapture: KArgumentCaptor<List<Record<*, *>>>
    private lateinit var cryptoService: CryptoService
    private lateinit var rewrapPublisher: Publisher
    private lateinit var config: Map<String, SmartConfig>

    private lateinit var stateManager: StateManager
    private lateinit var stateManagerCreateCapture: KArgumentCaptor<Collection<State>>
    private lateinit var stateManagerDeleteCapture: KArgumentCaptor<Collection<State>>

    // Some default fields
    private val oldKeyAlias = "oldKeyAlias"
    private val newKeyAlias = "newKeyAlias"
    private val tenantId = "tenantId"
    private val defaultMasterWrappingKeyAlias = "defaultKeyAlias"


    private val dummyUuidsAndAliases = List(4) { UUID.randomUUID().let { Pair(it, it.toString()) } }.toSet()

    class TestCordaAvroSerializationFactory : CordaAvroSerializationFactory {
        override fun <T : Any> createAvroSerializer(onError: ((ByteArray) -> Unit)?): CordaAvroSerializer<T> {
            return TestCordaAvroSerializer()
        }

        override fun <T : Any> createAvroDeserializer(
            onError: (ByteArray) -> Unit,
            expectedClass: Class<T>
        ): CordaAvroDeserializer<T> {
            TODO("Not needed")
        }

        inner class TestCordaAvroSerializer<T : Any> : CordaAvroSerializer<T> {
            override fun serialize(data: T): ByteArray? {
                serialized.add(data)
                return byteArrayOf(42)
            }
        }

        val serialized = mutableListOf<Any>()
    }

    private var cordaAvroSerializationFactory = TestCordaAvroSerializationFactory()

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

        virtualNodeInfoReadService = mock()

        val wrappingRepository: WrappingRepository = mock {
            on { findKeysNotWrappedByParentKey(any()) } doReturn listOf(
                WrappingKeyInfo(
                    0,
                    "",
                    byteArrayOf(),
                    0,
                    defaultMasterWrappingKeyAlias,
                    "alias1"
                )
            )
            on { getAllKeyIdsAndAliases() } doReturn dummyUuidsAndAliases
        }
        wrappingRepositoryFactory = mock {
            on { create(any()) } doReturn wrappingRepository
        }

        val signingRepository: SigningRepository = mock {
            on { getKeyMaterials(any()) } doReturn listOf(SigningKeyMaterialInfo(UUID.randomUUID(), "".toByteArray()))

        }
        signingRepositoryFactory = mock {
            on { getInstance(any()) } doReturn signingRepository
        }
        rewrapPublishCapture = argumentCaptor()
        rewrapPublisher = mock {
            on { publish(rewrapPublishCapture.capture()) } doReturn emptyList()
        }

        stateManagerCreateCapture = argumentCaptor()
        stateManagerDeleteCapture = argumentCaptor()
        stateManager = mock<StateManager>() {
            on { create(stateManagerCreateCapture.capture()) } doReturn emptySet()
            on { delete(stateManagerDeleteCapture.capture()) } doReturn emptyMap()
            on { isRunning } doReturn true
        }

        cryptoRekeyBusProcessor = CryptoRekeyBusProcessor(
            cryptoService,
            virtualNodeInfoReadService,
            wrappingRepositoryFactory,
            signingRepositoryFactory,
            rewrapPublisher,
            stateManager,
            cordaAvroSerializationFactory,
            defaultMasterWrappingKeyAlias
        )
    }

    @Test
    fun `unmanaged key rotation re-wraps all the keys and writes state`() {
        val virtualNodeTenantIds = listOf(tenantId1, tenantId2, tenantId3)
        val virtualNodes = getStubVirtualNodes(virtualNodeTenantIds)
        whenever(virtualNodeInfoReadService.getAll()).thenReturn(virtualNodes)

        cryptoRekeyBusProcessor.onNext(listOf(getUnmanagedKeyRotationKafkaRecord()))

        verify(rewrapPublisher, times(1)).publish(any())
        assertThat(rewrapPublishCapture.allValues).hasSize(1)

        val allTenants = virtualNodeTenantIds + CryptoTenants.CRYPTO
        assertThat(rewrapPublishCapture.firstValue).hasSize(allTenants.size)

        verify(stateManager, times(1)).delete(any())
        verify(stateManager, times(1)).create(any())

        assertThat(stateManagerCreateCapture.firstValue).hasSize(allTenants.size)

        stateManagerCreateCapture.firstValue.forEachIndexed { index, it ->
            assertThat(it.metadata[STATE_TYPE]).isEqualTo(UnmanagedKeyStatus::class.java.name)
            assertThat(it.metadata[KeyRotationMetadataValues.STATUS_TYPE]).isEqualTo(KeyRotationRecordType.KEY_ROTATION)
            assertThat(it.metadata[KeyRotationMetadataValues.STATUS]).isEqualTo(KeyRotationStatus.IN_PROGRESS)
            assertThat(it.metadata[KeyRotationMetadataValues.KEY_TYPE]).isEqualTo(KeyRotationKeyType.UNMANAGED)

            assertThat(it.key).isEqualTo(
                getKeyRotationStatusRecordKey(
                    defaultMasterWrappingKeyAlias,
                    allTenants[index]
                )
            )

            val unmanagedKeyStatus = (cordaAvroSerializationFactory.serialized[index] as? UnmanagedKeyStatus)
            assertThat(unmanagedKeyStatus).isNotNull()
            assertThat(unmanagedKeyStatus!!.tenantId).isEqualTo(allTenants[index])
            assertThat(unmanagedKeyStatus.total).isEqualTo(1)
            assertThat(unmanagedKeyStatus.rotatedKeys).isEqualTo(0)
        }
    }

    @Test
    fun `unmanaged key rotation handles bad tenant access`() {
        val virtualNodeTenantIds = listOf(tenantId1, tenantId2, tenantId3)
        val virtualNodes = getStubVirtualNodes(virtualNodeTenantIds)
        whenever(virtualNodeInfoReadService.getAll()).thenReturn(virtualNodes)

        // Create a WrappingRepository which throws before returning good keys
        val wrappingRepository = mock<WrappingRepository>()
        whenever(wrappingRepository.findKeysNotWrappedByParentKey(any())).thenThrow(IllegalStateException()).thenReturn(
            listOf(
                WrappingKeyInfo(
                    0,
                    "",
                    byteArrayOf(),
                    0,
                    oldKeyAlias,
                    "alias1"
                )
            )
        )
        whenever(wrappingRepositoryFactory.create(any())).thenReturn(wrappingRepository)

        cryptoRekeyBusProcessor.onNext(listOf(getUnmanagedKeyRotationKafkaRecord()))

        verify(rewrapPublisher, times(1)).publish(any())
        assertThat(rewrapPublishCapture.allValues).hasSize(1)

        val allTenantsExceptFirst = virtualNodeTenantIds.drop(1) + CryptoTenants.CRYPTO
        assertThat(rewrapPublishCapture.firstValue).hasSize(allTenantsExceptFirst.size)
    }

    @Test
    fun `unmanaged key rotation deletes any previous state`() {
        val simulatedExistingStateMap = dummyUuidsAndAliases.map {
            val key = getKeyRotationStatusRecordKey(
                it.second,
                "MyTenant"
            )
            Pair(key, State(key = key, value = byteArrayOf(42)))
        }.toMap()

        // first return is empty map, so we pass ongoing rotation detection
        whenever(stateManager.findByMetadataMatchingAll(any())).thenReturn(simulatedExistingStateMap)
        cryptoRekeyBusProcessor.onNext(listOf(getUnmanagedKeyRotationKafkaRecord()))
        verify(stateManager, times(1)).delete(any())

        assertThat(stateManagerDeleteCapture.firstValue.map { it.key }
            .toSet()).isEqualTo(simulatedExistingStateMap.keys)
    }

    /**
     * The test creates 4 tenants (3 vNodes and one cluster db), but when asks which ones parent_key_alias is not
     * default_master_wrapping_key, it returns wrapping key only for two tenants.
     * The other two tenants return null, simulating their parent_key_alias is already default_master_wrapping_key,
     * therefore no rewrap is needed.
     *
     * When onNext is then called, we correctly create only two records for publishing.
     */
    @Test
    fun `unmanaged key rotation re-wraps only those keys where parent key alias is not the default master key`() {
        val newId = UUID.randomUUID()
        val wrappingKeyInfo = WrappingKeyInfo(
            1, "caesar", SecureHashUtils.randomBytes(), 1,
            "notDefaultMasterWrappingKey", "alias1"
        )
        val savedWrappingKey = makeWrappingKeyEntity(newId, wrappingKeyInfo)
        // Mock the entity manager's functions used by findKeysNotWrappedByAlias
        val em1 = createEntityManager(listOf(savedWrappingKey))
        val repo1 = createWrappingRepo(em1, tenantId1)

        // We pass empty entity manager, so the request for a parent_key_alias being not default_key will return null
        val em2 = createEntityManager(listOf())

        // Repo2 returns empty list when requested if the parent_key_alias is not the default_master_wrapping_key
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
            signingRepositoryFactory,
            rewrapPublisher,
            stateManager,
            cordaAvroSerializationFactory,
            defaultMasterWrappingKeyAlias
        )

        cryptoRekeyBusProcessor.onNext(listOf(getUnmanagedKeyRotationKafkaRecord()))

        verify(rewrapPublisher, times(1)).publish(any())
        assertThat(rewrapPublishCapture.allValues).hasSize(1)
        assertThat(rewrapPublishCapture.firstValue).hasSize(2) // because we publish 2 records (tenantId1 and tenantId3)
    }

    @Test
    fun `unmanaged key rotation with non null tenant id is ignored`() {
        val virtualNodes = getStubVirtualNodes(listOf(tenantId1, tenantId2, tenantId3))
        whenever(virtualNodeInfoReadService.getAll()).thenReturn(virtualNodes)

        cryptoRekeyBusProcessor.onNext(listOf(getUnmanagedKeyRotationKafkaRecord(tenantId = "tenantId")))

        verify(rewrapPublisher, never()).publish(any())
    }

    private fun makeWrappingKeyEntity(
        newId: UUID,
        wrappingKeyInfo: WrappingKeyInfo,
    ): WrappingKeyEntity = WrappingKeyEntity(
        newId,
        wrappingKeyInfo.alias,
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

    private fun getUnmanagedKeyRotationKafkaRecord(
        tenantId: String? = null
    ): Record<String, KeyRotationRequest> = Record(
        "TBC",
        UUID.randomUUID().toString(),
        KeyRotationRequest(
            UUID.randomUUID().toString(),
            KeyType.UNMANAGED,
            tenantId,
        )
    )

    @Test
    fun `managed key rotation with null tenant id is ignored`() {
        val virtualNodes = getStubVirtualNodes(listOf(tenantId1, tenantId2, tenantId3))
        whenever(virtualNodeInfoReadService.getAll()).thenReturn(virtualNodes)

        cryptoRekeyBusProcessor.onNext(listOf(getManagedKeyRotationKafkaRecord(tenantId = null)))

        verify(rewrapPublisher, never()).publish(any())
    }

    @Test
    fun `managed key rotation with empty tenant id is ignored`() {
        val virtualNodes = getStubVirtualNodes(listOf(tenantId1, tenantId2, tenantId3))
        whenever(virtualNodeInfoReadService.getAll()).thenReturn(virtualNodes)

        cryptoRekeyBusProcessor.onNext(listOf(getManagedKeyRotationKafkaRecord(tenantId = "")))

        verify(rewrapPublisher, never()).publish(any())
    }

    @Test
    fun `managed key rotation issues messages to re-wrap all the keys and writes state`() {
        val tenantId = "MyTenant"
        cryptoRekeyBusProcessor.onNext(listOf(getManagedKeyRotationKafkaRecord(tenantId = tenantId)))

        verify(rewrapPublisher, times(1)).publish(any())

        assertThat(rewrapPublishCapture.allValues).hasSize(1)
        assertThat(rewrapPublishCapture.firstValue).hasSize(dummyUuidsAndAliases.size)

        rewrapPublishCapture.firstValue.map { it.value as IndividualKeyRotationRequest }.forEach {
            assertThat(it.keyType).isEqualTo(KeyType.MANAGED)
            assertThat(it.tenantId).isEqualTo(tenantId)
            assertThat(dummyUuidsAndAliases.map { it.first }.toSet()).contains(UUID.fromString(it.keyUuid))
        }

        verify(stateManager, times(1)).delete(any())
        verify(stateManager, times(1)).create(any())

        assertThat(stateManagerCreateCapture.firstValue).hasSize(dummyUuidsAndAliases.size)

        stateManagerCreateCapture.firstValue.forEachIndexed { index, it ->
            assertThat(it.metadata[STATE_TYPE]).isEqualTo(ManagedKeyStatus::class.java.name)
            assertThat(it.metadata[KeyRotationMetadataValues.TENANT_ID]).isEqualTo(tenantId)
            assertThat(it.metadata[KeyRotationMetadataValues.STATUS_TYPE]).isEqualTo(KeyRotationRecordType.KEY_ROTATION)
            assertThat(it.metadata[KeyRotationMetadataValues.STATUS]).isEqualTo(KeyRotationStatus.IN_PROGRESS)
            assertThat(it.metadata[KeyRotationMetadataValues.KEY_TYPE]).isEqualTo(KeyRotationKeyType.MANAGED)

            val alias = dummyUuidsAndAliases.toList()[index].second

            assertThat(it.key).isEqualTo(
                getKeyRotationStatusRecordKey(
                    alias,
                    tenantId
                )
            )

            val managedKeyStatus = (cordaAvroSerializationFactory.serialized[index] as? ManagedKeyStatus)
            assertThat(managedKeyStatus).isNotNull()
            assertThat(managedKeyStatus!!.wrappingKeyAlias).isEqualTo(alias)
            assertThat(managedKeyStatus.rotatedKeys).isEqualTo(0)
            assertThat(managedKeyStatus.total).isEqualTo(1) // number of materials mocked from getKeyMaterials
        }
    }

    @Test
    fun `managed key rotation deletes any previous state for this tenant`() {
        val tenantId = "MyTenant"
        val simulatedExistingStateMap = dummyUuidsAndAliases.map {
            val key = getKeyRotationStatusRecordKey(
                it.second,
                tenantId
            )
            Pair(key, State(key = key, value = byteArrayOf(42)))
        }.toMap()

        // first return is empty map, so we pass ongoing rotation detection
        whenever(stateManager.findByMetadataMatchingAll(any())).thenReturn(simulatedExistingStateMap)
        cryptoRekeyBusProcessor.onNext(listOf(getManagedKeyRotationKafkaRecord(tenantId = tenantId)))
        verify(stateManager, times(1)).delete(any())

        assertThat(stateManagerDeleteCapture.firstValue.map { it.key }
            .toSet()).isEqualTo(simulatedExistingStateMap.keys)
    }

    private fun getManagedKeyRotationKafkaRecord(
        tenantId: String? = this.tenantId
    ): Record<String, KeyRotationRequest> = Record(
        "TBC",
        UUID.randomUUID().toString(),
        KeyRotationRequest(
            UUID.randomUUID().toString(),
            KeyType.MANAGED,
            tenantId,
        )
    )
}
