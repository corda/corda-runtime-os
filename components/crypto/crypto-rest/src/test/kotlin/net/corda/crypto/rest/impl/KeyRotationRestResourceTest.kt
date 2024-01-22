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
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.schema.configuration.ConfigKeys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

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
    private val oldKeyAlias = "oldKeyAlias"
    private val newKeyAlias = "newKeyAlias"
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
            on { deserialize(any()) } doReturn UnmanagedKeyStatus(oldKeyAlias, 10, 5)
        }

        cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
            on { createAvroDeserializer<UnmanagedKeyStatus>(any(), any()) } doReturn deserializer
        }

        stateManagerFactory = mock<StateManagerFactory> {
            on { create(any()) } doReturn stateManager
        }

        stateManagerPublicationCount = 0
    }

    @Test
    fun `get key rotation status triggers successfully`() {
        val keyRotationRestResource = createKeyRotationRestResource()
        val response = keyRotationRestResource.getKeyRotationStatus(oldKeyAlias)

        verify(stateManager, times(1)).findByMetadataMatchingAll(any())

        assertThat(response.status).isEqualTo(KeyRotationStatus.IN_PROGRESS)
        assertThat(response.rootKeyAlias).isEqualTo(oldKeyAlias)
    }

    @Test
    fun `get key rotation status for never rotated keyAlias throws`() {
        val keyRotationRestResource = createKeyRotationRestResource()
        whenever(stateManager.findByMetadataMatchingAll(any())).thenReturn(emptyMap())
        assertThrows<ResourceNotFoundException> {
            keyRotationRestResource.getKeyRotationStatus("someRandomKeyAlias")
        }
    }

    @Test
    fun `get key rotation status throws when state manager is not initialised`() {
        val keyRotationRestResource =
            createKeyRotationRestResource(initialiseKafkaPublisher = true, initialiseStateManager = false)
        assertThrows<IllegalStateException> {
            keyRotationRestResource.getKeyRotationStatus("")
        }
        verify(stateManager, never()).findByMetadataMatchingAll(any())
    }

    @Test
    fun `initialize creates the publisher and state manager`() {
        createKeyRotationRestResource()
        verify(publisherFactory, times(1)).createPublisher(any(), any())
        verify(stateManagerFactory, times(1)).create(any())
    }

    @Test
    fun `start key rotation event triggers successfully`() {
        val records = mutableListOf<Record<String, KeyRotationRequest>>()
        doKeyRotation(oldKeyAlias, newKeyAlias, { records.addAll(it) })
        assertThat(records.size).isEqualTo(1)
    }


    @Test
    fun `start key rotation event throws when kafka publisher is not initialised`() {
        val keyRotationRestResource =
            createKeyRotationRestResource(initialiseKafkaPublisher = false, initialiseStateManager = true)
        assertThrows<InternalServerException> {
            keyRotationRestResource.startKeyRotation("", "")
        }
        verify(publishToKafka, never()).publish(any())
        assertThat(stateManagerPublicationCount).isEqualTo(0)
    }

    @Test
    fun `start key rotation event throws when state manager is not initialised`() {
        val keyRotationRestResource =
            createKeyRotationRestResource(initialiseKafkaPublisher = true, initialiseStateManager = false)
        assertThrows<IllegalStateException> {
            keyRotationRestResource.startKeyRotation("", "")
        }
        verify(publishToKafka, never()).publish(any())
        assertThat(stateManagerPublicationCount).isEqualTo(0)
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

    // We need two wrapping key aliases - oldKeyAlias and newKeyAlias
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

    // MESSAGING_CONFIG just needs to be initialised without any particular data
    private fun createMessagingConfig(): SmartConfig =
        SmartConfigFactory.createWithoutSecurityServices().create(ConfigFactory.empty())
            .withValue(
                ConfigKeys.MESSAGING_CONFIG, ConfigValueFactory.fromAnyRef("random")
            )
}
