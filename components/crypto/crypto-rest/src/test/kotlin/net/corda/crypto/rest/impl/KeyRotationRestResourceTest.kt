package net.corda.crypto.rest.impl

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.config.impl.CryptoHSMConfig
import net.corda.crypto.config.impl.HSM
import net.corda.crypto.core.KeyRotationStatus
import net.corda.crypto.core.MASTER_WRAPPING_KEY_ROTATION_IDENTIFIER
import net.corda.crypto.rest.KeyRotationRestResource
import net.corda.data.crypto.wire.ops.key.rotation.KeyRotationRequest
import net.corda.data.crypto.wire.ops.key.status.UnmanagedKeyStatus
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.test.impl.LifecycleTest
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.rest.exception.InternalServerException
import net.corda.rest.exception.InvalidInputDataException
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.StateManagerConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class KeyRotationRestResourceTest {

    private lateinit var publisherFactory: PublisherFactory
    private lateinit var publishToKafka: Publisher
    private lateinit var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
    private lateinit var lifecycleCoordinator: LifecycleCoordinator
    private val configurationReadService = mock<ConfigurationReadService>()
    private lateinit var deserializer: CordaAvroDeserializer<UnmanagedKeyStatus>
    private lateinit var cordaAvroSerializationFactory: CordaAvroSerializationFactory
    private lateinit var stateManager: StateManager
    private lateinit var stateManagerFactory: StateManagerFactory

    private lateinit var config: Map<String, SmartConfig>
    private val defaultMasterKeyAlias = "rootAlias1"
    private val masterKeyAlias2 = "rootAlias2"
    private val masterKeyAlias3 = "rootAlias3"
    private val tenantId = "tenantId"
    private var stateManagerPublicationCount: Int = 0

    @BeforeEach
    fun setup() {
        publisherFactory = mock()
        publishToKafka = mock()
        lifecycleCoordinatorFactory = mock()
        lifecycleCoordinator = mock()

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
                        ),
                ConfigKeys.STATE_MANAGER_CONFIG to
                        SmartConfigFactory.createWithoutSecurityServices().create(
                            createMessagingConfig()
                        )
            )
        )
        config = configEvent.config

        whenever(publisherFactory.createPublisher(any(), any())).thenReturn(publishToKafka)

        stateManager = mock<StateManager> {
            on { create(any()) } doReturn emptySet()
            on { findByMetadataMatchingAll(any()) } doReturn mapOf(
                "random" to State(
                    "random",
                    "random".toByteArray(),
                    0,
                    Metadata(mapOf("status" to "In Progress"))
                )
            )
        }

        deserializer = mock<CordaAvroDeserializer<UnmanagedKeyStatus>> {
            on { deserialize(any()) } doReturn UnmanagedKeyStatus(
                MASTER_WRAPPING_KEY_ROTATION_IDENTIFIER,
                null,
                tenantId,
                10,
                5,
                Instant.now()
            )
        }

        cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
            on { createAvroDeserializer<UnmanagedKeyStatus>(any(), any()) } doReturn deserializer
        }

        stateManagerFactory = mock<StateManagerFactory> {
            on { create(any(), eq(StateManagerConfig.StateType.KEY_ROTATION)) } doReturn stateManager
        }

        stateManagerPublicationCount = 0
    }

    @Test
    fun `get key rotation status triggers successfully`() {
        val keyRotationRestResource = createKeyRotationRestResource()
        val response = keyRotationRestResource.getKeyRotationStatus(MASTER_WRAPPING_KEY_ROTATION_IDENTIFIER)

        verify(stateManager, times(1)).findByMetadataMatchingAll(any())

        assertThat(response.status).isEqualTo(KeyRotationStatus.IN_PROGRESS)
        assertThat(response.tenantId).isEqualTo(MASTER_WRAPPING_KEY_ROTATION_IDENTIFIER)
    }

    @Test
    fun `get managed key rotation status for never rotated tenantId throws`() {
        val keyRotationRestResource = createKeyRotationRestResource()
        whenever(stateManager.findByMetadataMatchingAll(any())).thenReturn(emptyMap())
        assertThrows<ResourceNotFoundException> {
            keyRotationRestResource.getKeyRotationStatus("someRandomTenantId")
        }
    }

    @Test
    fun `get key rotation status throws when state manager is not initialised`() {
        val keyRotationRestResource =
            createKeyRotationRestResource(initialiseKafkaPublisher = true, initialiseStateManager = false)
        assertThrows<IllegalStateException> {
            keyRotationRestResource.getKeyRotationStatus(MASTER_WRAPPING_KEY_ROTATION_IDENTIFIER)
        }
        verify(stateManager, never()).findByMetadataMatchingAll(any())
    }

    @Test
    fun `start unmanaged key rotation event triggers successfully`() {
        val records = mutableListOf<Record<String, KeyRotationRequest>>()
        doKeyRotation({ records.addAll(it) })
        assertThat(records.size).isEqualTo(1)
    }

    @Test
    fun `start managed key rotation event triggers successfully`() {
        val records = mutableListOf<Record<String, KeyRotationRequest>>()
        doManagedKeyRotation(tenantId, { records.addAll(it) })
        assertThat(records.size).isEqualTo(1)
    }

    @Test
    fun `start key rotation event throws when kafka publisher is not initialised`() {
        val keyRotationRestResource =
            createKeyRotationRestResource(initialiseKafkaPublisher = false, initialiseStateManager = true)
        assertThrows<InternalServerException> {
            keyRotationRestResource.startKeyRotation(tenantId)
        }
        verify(publishToKafka, never()).publish(any())
        assertThat(stateManagerPublicationCount).isEqualTo(0)
    }

    @Test
    fun `start key rotation event throws when state manager is not initialised`() {
        val keyRotationRestResource =
            createKeyRotationRestResource(initialiseKafkaPublisher = true, initialiseStateManager = false)
        assertThrows<IllegalStateException> {
            keyRotationRestResource.startKeyRotation(tenantId)
        }
        verify(publishToKafka, never()).publish(any())
        assertThat(stateManagerPublicationCount).isEqualTo(0)
    }

    @Test
    fun `start managed key rotation event throws when tenantId is empty string`() {
        val keyRotationRestResource = createKeyRotationRestResource()
        whenever(stateManager.findByMetadataMatchingAll(any())).thenReturn(emptyMap())
        assertThrows<InvalidInputDataException> {
            keyRotationRestResource.startKeyRotation("")
        }
        verify(publishToKafka, never()).publish(any())
        assertThat(stateManagerPublicationCount).isEqualTo(0)
    }

    @Test
    fun `initialize creates the publisher and state manager`() {
        createKeyRotationRestResource()
        verify(publisherFactory, times(1)).createPublisher(any(), any())
        verify(stateManagerFactory, times(1)).create(any(), eq(StateManagerConfig.StateType.KEY_ROTATION))
    }

    @Test
    fun `start event doesnt post up status before being initialised`() {
        val context = getKeyRotationRestResourceTestContext()
        context.run {
            testClass.start()
            context.verifyIsDown<KeyRotationRestResource>()
        }
    }

    @Test
    fun `start event posts up status after all components are up`() {
        val context = getKeyRotationRestResourceTestContext()
        context.run {
            testClass.start()
            bringDependenciesUp()
            context.verifyIsUp<KeyRotationRestResource>()
        }
    }

    private fun createKeyRotationRestResource(
        initialiseKafkaPublisher: Boolean = true,
        initialiseStateManager: Boolean = true
    ): KeyRotationRestResource {
        return KeyRotationRestResourceImpl(
            mock(),
            publisherFactory,
            lifecycleCoordinatorFactory,
            configurationReadService,
            stateManagerFactory,
            cordaAvroSerializationFactory
        ).apply {
            if (initialiseKafkaPublisher) initialiseKafkaPublisher(config)
            if (initialiseStateManager) initialiseStateManager(config)
        }
    }

    private fun getKeyRotationRestResourceTestContext(): LifecycleTest<KeyRotationRestResourceImpl> {
        return LifecycleTest {
            addDependency<LifecycleCoordinatorFactory>()
            addDependency<ConfigurationReadService>()

            KeyRotationRestResourceImpl(
                mock(),
                publisherFactory,
                coordinatorFactory, // This is from the test lifecycle class.
                configurationReadService,
                mock(),
                mock()
            )
        }
    }

    // We need two wrapping key aliases - masterKeyAlias2 and masterKeyAlias3
    private fun createCryptoConfig(wrappingKeyPassphrase: Any, wrappingKeySalt: Any): SmartConfig =
        SmartConfigFactory.createWithoutSecurityServices().create(ConfigFactory.empty())
            .withValue(
                HSM, ConfigValueFactory.fromMap(
                    mapOf(
                        CryptoHSMConfig::defaultWrappingKey.name to ConfigValueFactory.fromAnyRef(defaultMasterKeyAlias),
                        CryptoHSMConfig::wrappingKeys.name to listOf(
                            ConfigValueFactory.fromAnyRef(
                                mapOf(
                                    "alias" to masterKeyAlias2,
                                    "salt" to wrappingKeySalt,
                                    "passphrase" to wrappingKeyPassphrase,
                                )
                            ),
                            ConfigValueFactory.fromAnyRef(
                                mapOf(
                                    "alias" to masterKeyAlias3,
                                    "salt" to wrappingKeySalt,
                                    "passphrase" to wrappingKeyPassphrase,
                                )
                            )
                        )
                    )
                )
            )

    // MESSAGING_CONFIG just needs to be initialised without any particular data
    private fun createMessagingConfig(): SmartConfig =
        SmartConfigFactory.createWithoutSecurityServices().create(ConfigFactory.empty())
            .withValue(
                ConfigKeys.MESSAGING_CONFIG, ConfigValueFactory.fromAnyRef("random")
            )
}
