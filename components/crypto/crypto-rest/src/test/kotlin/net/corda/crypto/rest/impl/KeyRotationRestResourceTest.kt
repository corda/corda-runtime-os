package net.corda.crypto.rest.impl

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.config.impl.CryptoHSMConfig
import net.corda.crypto.config.impl.HSM
import net.corda.crypto.rest.KeyRotationRestResource
import net.corda.data.crypto.wire.ops.key.rotation.KeyRotationRequest
import net.corda.data.crypto.wire.ops.key.rotation.KeyRotationStatus
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.test.impl.LifecycleTest
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.ConfigKeys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class KeyRotationRestResourceTest {

    private lateinit var publisherFactory: PublisherFactory
    private lateinit var publishToKafka: Publisher
    private lateinit var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
    private lateinit var lifecycleCoordinator: LifecycleCoordinator
    private val configurationReadService = mock<ConfigurationReadService>()
    private val cordaAvroSerializer = mock<CordaAvroSerializer<KeyRotationStatus>>()
    private val mockCordaAvroSerializationFactory = mock<CordaAvroSerializationFactory>().also {
        whenever(it.createAvroSerializer<KeyRotationStatus>(anyOrNull())).thenReturn(
            cordaAvroSerializer
        )
    }
    private lateinit var stateManager: StateManager
    private lateinit var stateManagerFactory: StateManagerFactory

    private lateinit var config: Map<String, SmartConfig>
    private val oldKeyAlias = "oldKeyAlias"
    private val newKeyAlias = "newKeyAlias"
    private var stateManagerPublicationCount:Int = 0

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
                        )
            )
        )
        config = configEvent.config

        whenever(publisherFactory.createPublisher(any(), any())).thenReturn(publishToKafka)

        val byteArray = "KeyRotationStatusSerialized".toByteArray()
        whenever(cordaAvroSerializer.serialize(any<KeyRotationStatus>())).thenReturn(byteArray)

        stateManager = mock<StateManager> {
            on { create(any()) } doReturn emptyMap()
        }

        stateManagerFactory = mock<StateManagerFactory>().also {
            whenever(it.create(any())).thenReturn(stateManager)
        }
        stateManagerPublicationCount = 0
    }

    @Disabled
    @Test
    fun `get key rotation status`() {
        TODO("Not yet implemented")
    }

    @Disabled
    @Test
    fun `get key rotation status for unknown requestID throws`() {
        TODO("Not yet implemented")
    }

    @Test
    fun `initialize creates the publisher`() {
        createKeyRotationRestResource()
        verify(publisherFactory, times(1)).createPublisher(any(), any())
    }

    @Test
    fun `start key rotation event triggers successfully`() {
        val states = mutableListOf<State>()
        val records = mutableListOf<Record<String, KeyRotationRequest>>()
        doKeyRotation(oldKeyAlias, newKeyAlias, { byteArrayOf(42)}, { states.addAll(it) }, { records.addAll(it) })
        assertThat(states.size).isEqualTo(1)
        assertThat(records.size).isEqualTo(1)
    }


    @Test
    fun `start key rotation event throws when not initialised`() {
        val keyRotationRestResource = createKeyRotationRestResource(false)
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

    private fun createKeyRotationRestResource(initialise: Boolean = true): KeyRotationRestResource {
        return KeyRotationRestResourceImpl(
            mock(),
            publisherFactory,
            lifecycleCoordinatorFactory,
            configurationReadService,
            stateManagerFactory,
            mockCordaAvroSerializationFactory
        ).apply { if (initialise) {
            initialise(config)
            }
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
