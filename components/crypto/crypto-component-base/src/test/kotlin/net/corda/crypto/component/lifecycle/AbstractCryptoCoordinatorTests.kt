package net.corda.crypto.component.lifecycle

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig
import net.corda.v5.cipher.suite.lifecycle.CryptoLifecycleComponent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AbstractCryptoCoordinatorTests {
    private lateinit var coordinator: LifecycleCoordinator
    private lateinit var coordinatorFactory: LifecycleCoordinatorFactory
    private lateinit var configurationReadService: ConfigurationReadService
    private lateinit var configChangeHandler: ConfigurationHandler
    private lateinit var registrationHandle: AutoCloseable
    private var coordinatorIsRunning = false
    private val configFactory = SmartConfigFactory.create(ConfigFactory.empty())

    @BeforeEach
    fun setup() {
        coordinator = mock()
        coordinatorFactory = mock()
        configurationReadService = mock()
        registrationHandle = mock()
        whenever(
            coordinatorFactory.createCoordinator(any(), any())
        ).thenReturn(coordinator)
        whenever(
            configurationReadService.registerForUpdates(any())
        ).then {
            configChangeHandler = it.getArgument(0, ConfigurationHandler::class.java)
            registrationHandle
        }
        whenever(
            coordinator.start()
        ).then {
            coordinatorIsRunning = true
            Any()
        }
        whenever(
            coordinator.stop()
        ).then {
            coordinatorIsRunning = false
            Any()
        }
        whenever(
            coordinator.isRunning
        ).then {
            coordinatorIsRunning
        }
    }

    @Test
    @Timeout(5)
    @Suppress("MaxLineLength")
    fun `Should register for config changes when on LifecycleStatus_UP event and does not start subcomponents yet`() {
        val subcomponents = listOf(
            mock<Lifecycle>()
        )
        setupCoordinator(subcomponents)
        coordinator.postEvent(RegistrationStatusChangeEvent(
            registration = mock(),
            status = LifecycleStatus.UP
        ))
        Mockito.verify(configurationReadService, times(1)).registerForUpdates(any())
        Mockito.verify(subcomponents[0], never()).start()
    }

    @Test
    @Timeout(5)
    @Suppress("MaxLineLength")
    fun `Should register for config changes when on LifecycleStatus_UP event and does not pass any configuration to subcomponent yet`() {
        val subcomponents = listOf(
            mock<CryptoLifecycleComponent>()
        )
        setupCoordinator(subcomponents)
        coordinator.postEvent(RegistrationStatusChangeEvent(
            registration = mock(),
            status = LifecycleStatus.UP
        ))
        Mockito.verify(configurationReadService, times(1)).registerForUpdates(any())
        Mockito.verify(subcomponents[0], never()).handleConfigEvent(any())
    }

    @Test
    @Timeout(5)
    fun `Should pass the configuration change to component which implements only CryptoLifecycleComponent`() {
        val subcomponents = listOf(
            mock<CryptoLifecycleComponent>(),
            mock<CryptoLifecycleComponent>(),
            Any()
        )
        setupCoordinator(subcomponents)
        coordinator.postEvent(RegistrationStatusChangeEvent(
            registration = mock(),
            status = LifecycleStatus.UP
        ))
        Mockito.verify(configurationReadService, times(1)).registerForUpdates(any())
        configChangeHandler.onNewConfiguration(
            setOf(AbstractCryptoCoordinator.CRYPTO_CONFIG),
            mapOf(
                AbstractCryptoCoordinator.CRYPTO_CONFIG to configFactory.create(ConfigFactory.parseMap(
                    mapOf(
                        "some-custom-key" to "some-custom-value"
                    )
                ))
            )
        )
        val configHandleCaptor0 = argumentCaptor<CryptoLibraryConfig>()
        val configHandleCaptor1 = argumentCaptor<CryptoLibraryConfig>()
        Mockito.verify(subcomponents[0] as CryptoLifecycleComponent, times(1))
            .handleConfigEvent(configHandleCaptor0.capture())
        Mockito.verify(subcomponents[1] as CryptoLifecycleComponent, times(1))
            .handleConfigEvent(configHandleCaptor1.capture())
        assertEquals("some-custom-value", configHandleCaptor0.firstValue["some-custom-key"])
        assertEquals("some-custom-value", configHandleCaptor1.firstValue["some-custom-key"])
    }

    @Test
    @Timeout(5)
    @Suppress("MaxLineLength")
    fun `Should start and pass the configuration change to component which implements CryptoLifecycleComponent and Lifecycle`() {
        val subcomponents = listOf(
            mock<FullCryptoLifecycleComponent>(),
            mock<CryptoLifecycleComponent>(),
            Any()
        )
        setupCoordinator(subcomponents)
        coordinator.postEvent(RegistrationStatusChangeEvent(
            registration = mock(),
            status = LifecycleStatus.UP
        ))
        Mockito.verify(configurationReadService, times(1)).registerForUpdates(any())
        configChangeHandler.onNewConfiguration(
            setOf(AbstractCryptoCoordinator.CRYPTO_CONFIG),
            mapOf(
                AbstractCryptoCoordinator.CRYPTO_CONFIG to configFactory.create(ConfigFactory.parseMap(
                    mapOf(
                        "some-custom-key" to "some-custom-value"
                    )
                ))
            )
        )
        val configHandleCaptor0 = argumentCaptor<CryptoLibraryConfig>()
        val configHandleCaptor1 = argumentCaptor<CryptoLibraryConfig>()
        Mockito.verify(subcomponents[0] as Lifecycle, times(1)).start()
        Mockito.verify(subcomponents[0] as CryptoLifecycleComponent, times(1))
            .handleConfigEvent(configHandleCaptor0.capture())
        Mockito.verify(subcomponents[1] as CryptoLifecycleComponent, times(1))
            .handleConfigEvent(configHandleCaptor1.capture())
        assertEquals("some-custom-value", configHandleCaptor0.firstValue["some-custom-key"])
        assertEquals("some-custom-value", configHandleCaptor1.firstValue["some-custom-key"])
    }

    @Test
    @Timeout(5)
    fun `Should throw IllegalStateException when config change for crypto library is empty`() {
        val subcomponents = listOf(
            mock<FullCryptoLifecycleComponent>(),
            mock<CryptoLifecycleComponent>(),
            Any()
        )
        setupCoordinator(subcomponents)
        coordinator.postEvent(RegistrationStatusChangeEvent(
            registration = mock(),
            status = LifecycleStatus.UP
        ))
        Mockito.verify(configurationReadService, times(1)).registerForUpdates(any())
        assertThrows<IllegalStateException> {
            configChangeHandler.onNewConfiguration(
                setOf(
                    AbstractCryptoCoordinator.CRYPTO_CONFIG
                ),
                mapOf(
                    AbstractCryptoCoordinator.CRYPTO_CONFIG to configFactory.create(ConfigFactory.empty())
                )
            )
        }
        Mockito.verify(subcomponents[0] as Lifecycle, never()).start()
        Mockito.verify(subcomponents[0] as CryptoLifecycleComponent, never()).handleConfigEvent(any())
        Mockito.verify(subcomponents[1] as CryptoLifecycleComponent, never()).handleConfigEvent(any())
    }

    @Test
    @Timeout(5)
    fun `Should do nothing when config change does not contain changes for crypto library`() {
        val subcomponents = listOf(
            mock<FullCryptoLifecycleComponent>(),
            mock<CryptoLifecycleComponent>(),
            Any()
        )
        setupCoordinator(subcomponents)
        coordinator.postEvent(RegistrationStatusChangeEvent(
            registration = mock(),
            status = LifecycleStatus.UP
        ))
        Mockito.verify(configurationReadService, times(1)).registerForUpdates(any())
        configChangeHandler.onNewConfiguration(
            setOf("whatever"),
            mapOf(
                "whatever" to configFactory.create(ConfigFactory.parseMap(
                    mapOf(
                        "some-custom-key" to "some-custom-value"
                    )
                )
            )
            )
        )
        Mockito.verify(subcomponents[0] as Lifecycle, never()).start()
        Mockito.verify(subcomponents[0] as CryptoLifecycleComponent, never()).handleConfigEvent(any())
        Mockito.verify(subcomponents[1] as CryptoLifecycleComponent, never()).handleConfigEvent(any())
    }

    @Test
    @Timeout(5)
    fun `Should close all AutoCloseable subcomponents`() {
        val subcomponents = listOf(
            mock<AutoCloseable>(),
            mock<CryptoLifecycleComponent>(),
            mock<AutoCloseable>()
        )
        val cut = setupCoordinator(subcomponents)
        coordinator.postEvent(RegistrationStatusChangeEvent(
            registration = mock(),
            status = LifecycleStatus.UP
        ))
        Mockito.verify(configurationReadService, times(1)).registerForUpdates(any())
        cut.close()
        assertFalse(cut.isRunning)
        Mockito.verify(registrationHandle, times(1)).close()
        Mockito.verify(subcomponents[0] as AutoCloseable, times(1)).close()
        Mockito.verify(subcomponents[2] as AutoCloseable, times(1)).close()
    }

    @Test
    @Timeout(5)
    fun `Should close config registration handler on receiving LifecycleStatus_DOWN event`() {
        val subcomponents = listOf(
            mock<AutoCloseable>(),
            mock<CryptoLifecycleComponent>(),
            mock<AutoCloseable>()
        )
        val cut = setupCoordinator(subcomponents)
        coordinator.postEvent(RegistrationStatusChangeEvent(
            registration = mock(),
            status = LifecycleStatus.UP
        ))
        Mockito.verify(configurationReadService, times(1)).registerForUpdates(any())
        coordinator.postEvent(RegistrationStatusChangeEvent(
            registration = mock(),
            status = LifecycleStatus.DOWN
        ))
        assertTrue(cut.isRunning)
        Mockito.verify(registrationHandle, times(1)).close()
        Mockito.verify(subcomponents[0] as AutoCloseable, never()).close()
        Mockito.verify(subcomponents[2] as AutoCloseable, never()).close()
    }

    @Test
    @Timeout(5)
    fun `Should not fail if receives LifecycleStatus_DOWN event on start`() {
        val subcomponents = listOf(
            mock<AutoCloseable>(),
            mock<CryptoLifecycleComponent>(),
            mock<AutoCloseable>()
        )
        setupCoordinator(subcomponents)
        coordinator.postEvent(RegistrationStatusChangeEvent(
            registration = mock(),
            status = LifecycleStatus.ERROR
        ))
        Mockito.verify(configurationReadService, never()).registerForUpdates(any())
        Mockito.verify(registrationHandle, never()).close()
        Mockito.verify(subcomponents[0] as AutoCloseable, never()).close()
        Mockito.verify(subcomponents[2] as AutoCloseable, never()).close()
    }

    private fun setupCoordinator(subcomponents: List<Any>): CryptoCoordinatorStub {
        val cut = CryptoCoordinatorStub(
            coordinatorFactory,
            configurationReadService,
            subcomponents
        )
        whenever(
            coordinator.postEvent(any())
        ).then {
            val event = it.getArgument(0, LifecycleEvent::class.java)
            cut.callHandleEvent(event)
        }
        cut.start()
        assertTrue(cut.isRunning)
        return cut
    }

    private interface FullCryptoLifecycleComponent : CryptoLifecycleComponent, Lifecycle

    private class CryptoCoordinatorStub(
        coordinatorFactory: LifecycleCoordinatorFactory,
        configurationReadService: ConfigurationReadService,
        subcomponents: List<Any>
    ): AbstractCryptoCoordinator(
        LifecycleCoordinatorName.forComponent<CryptoCoordinatorStub>(),
        coordinatorFactory,
        configurationReadService,
        subcomponents
    ) {
        fun callHandleEvent(event: LifecycleEvent) =
            handleEvent(event)
    }
}