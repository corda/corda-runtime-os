package net.corda.crypto.rest.impl

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.config.impl.CryptoHSMConfig
import net.corda.crypto.config.impl.HSM
import net.corda.crypto.rest.KeyRotationRestResource
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.test.impl.LifecycleTest
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.rest.exception.ServiceUnavailableException
import net.corda.schema.configuration.ConfigKeys
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class KeyRotationRestResourceTest {

    private lateinit var publisherFactory: PublisherFactory
    private lateinit var publisher: Publisher
    private lateinit var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
    private lateinit var lifecycleCoordinator: LifecycleCoordinator
    private val configurationReadService = mock<ConfigurationReadService>()
    private lateinit var config: Map<String, SmartConfig>
    private val oldKeyAlias = "oldKeyAlias"
    private val newKeyAlias = "newKeyAlias"

    @BeforeEach
    fun setup() {
        publisherFactory = mock()
        publisher = mock()
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

        whenever(publisherFactory.createPublisher(any(), any())).thenReturn(publisher)
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
        val keyRotationRestResource = createKeyRotationRestResource()
        keyRotationRestResource.startKeyRotation(oldKeyAlias, newKeyAlias)

        verify(publisher, times(1)).publish(any())
    }


    @Test
    fun `start key rotation event throws when not initialised`() {
        val keyRotationRestResource = createKeyRotationRestResource(false)
        assertThrows<ServiceUnavailableException> {
            keyRotationRestResource.startKeyRotation("", "")
        }
        verify(publisher, never()).publish(any())
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
            configurationReadService
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
                configurationReadService
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
