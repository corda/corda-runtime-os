package net.corda.crypto.service.impl.bus

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.crypto.config.impl.CryptoHSMConfig
import net.corda.crypto.config.impl.HSM
import net.corda.crypto.core.CryptoService
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.softhsm.WrappingRepository
import net.corda.crypto.softhsm.WrappingRepositoryFactory
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
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class CryptoRekeyBusProcessorTests {
    private lateinit var tenantId: String
    private lateinit var cryptoRekeyBusProcessor: CryptoRekeyBusProcessor
    private lateinit var virtualNodeInfoReadService: VirtualNodeInfoReadService
    private lateinit var wrappingRepositoryFactory: WrappingRepositoryFactory
    private lateinit var publisherFactory: PublisherFactory
    private lateinit var publisher: Publisher
    private lateinit var config: Map<String, SmartConfig>
    private val oldKeyAlias = "oldKeyAlias"
    private val newKeyAlias = "newKeyAlias"

    @BeforeEach
    fun setup() {
        val configEvent = ConfigChangedEvent(
            setOf(ConfigKeys.CRYPTO_CONFIG, ConfigKeys.MESSAGING_CONFIG),
            mapOf(
                ConfigKeys.CRYPTO_CONFIG to
                        SmartConfigFactory.createWithoutSecurityServices().create(
                            createCryptoConfig("pass", "salt")
                        ),
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

        val wrappingRepository: WrappingRepository = mock() {
            on { findKey(any()) } doReturn WrappingKeyInfo(0, "", byteArrayOf(), 0, oldKeyAlias)
        }

        wrappingRepositoryFactory = mock() {
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
    fun `limit for key rotation operations is reflected`() {
        val virtualNodes = getStubVirtualNodes(listOf("Alice", "Bob", "Charlie", "David", "Erin"))
        whenever(virtualNodeInfoReadService.getAll()).thenReturn(virtualNodes)

        cryptoRekeyBusProcessor.onNext(listOf(getKafkaRecord("", 2)))

        verify(publisher, times(2)).publish(any())
    }

    @Test
    fun `key rotation posts new Kafka message only for those tenantIds that has key with oldKeyAlias alias`() {
        // to be implemented
    }

    private fun createCryptoConfig(wrappingKeyPassphrase: Any, wrappingKeySalt: Any): SmartConfig =
        SmartConfigFactory.createWithoutSecurityServices().create(ConfigFactory.empty())
            .withValue(
                HSM, ConfigValueFactory.fromMap(
                    mapOf(
                        CryptoHSMConfig::defaultWrappingKey.name to ConfigValueFactory.fromAnyRef(oldKeyAlias),
                        CryptoHSMConfig::wrappingKeys.name to listOf(
                            ConfigValueFactory.fromAnyRef(
                                mapOf(
                                    "alias" to oldKeyAlias,
                                    "salt" to wrappingKeySalt,
                                    "passphrase" to wrappingKeyPassphrase,
                                )
                            ),
                            ConfigValueFactory.fromAnyRef(
                                mapOf(
                                    "alias" to newKeyAlias,
                                    "salt" to wrappingKeySalt,
                                    "passphrase" to wrappingKeyPassphrase,
                                )
                            )
                        )
                    )
                )
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
