package net.corda.crypto.client

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.component.config.rpc
import net.corda.crypto.component.lifecycle.AbstractCryptoCoordinator
import net.corda.crypto.impl.config.defaultCryptoService
import net.corda.crypto.impl.config.isDev
import net.corda.crypto.impl.config.publicKeys
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig
import net.corda.v5.cipher.suite.lifecycle.CryptoLifecycleComponent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CryptoLibraryCoordinatorTests {
    private lateinit var coordinator: LifecycleCoordinator
    private lateinit var coordinatorFactory: LifecycleCoordinatorFactory
    private lateinit var configurationReadService: ConfigurationReadService
    private lateinit var cipherSuiteFactory: CipherSuiteFactoryStub
    private lateinit var cryptoFactoryProvider: CryptoLibraryClientsFactoryProviderImpl
    private lateinit var configChangeHandler: ConfigurationHandler
    private lateinit var registrationHandle: AutoCloseable
    private var coordinatorIsRunning = false

    @BeforeEach
    fun setup() {
        coordinator = mock()
        coordinatorFactory = mock()
        configurationReadService = mock()
        cipherSuiteFactory = mock()
        cryptoFactoryProvider = mock()
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
    fun `Should create default library configuration when config change for crypto library is empty`() {
        setupCoordinator()
        coordinator.postEvent(
            RegistrationStatusChangeEvent(
                registration = mock(),
                status = LifecycleStatus.UP
            )
        )
        Mockito.verify(configurationReadService, times(1)).registerForUpdates(any())

        val configFactory = SmartConfigFactory.create(ConfigFactory.empty())
        configChangeHandler.onNewConfiguration(
            setOf(
                AbstractCryptoCoordinator.CRYPTO_CONFIG
            ),
            mapOf(
                AbstractCryptoCoordinator.CRYPTO_CONFIG to configFactory.create(ConfigFactory.empty())
            )
        )
        val cipherSuiteFactoryCaptor = argumentCaptor<CryptoLibraryConfig>()
        val cryptoFactoryProviderCaptor = argumentCaptor<CryptoLibraryConfig>()
        Mockito.verify(cipherSuiteFactory as Lifecycle, times(1)).start()
        Mockito.verify(cipherSuiteFactory as CryptoLifecycleComponent, times(1))
            .handleConfigEvent(cipherSuiteFactoryCaptor.capture())
        Mockito.verify(cryptoFactoryProvider as Lifecycle, times(1)).start()
        Mockito.verify(cryptoFactoryProvider as CryptoLifecycleComponent, times(1))
            .handleConfigEvent(cryptoFactoryProviderCaptor.capture())
        assertTrue(cipherSuiteFactoryCaptor.firstValue.isDev)
        assertTrue(cipherSuiteFactoryCaptor.firstValue.defaultCryptoService.isEmpty())
        assertTrue(cipherSuiteFactoryCaptor.firstValue.publicKeys.isEmpty())
        assertTrue(cipherSuiteFactoryCaptor.firstValue.rpc.isEmpty())
        assertTrue(cryptoFactoryProviderCaptor.firstValue.isDev)
        assertTrue(cryptoFactoryProviderCaptor.firstValue.defaultCryptoService.isEmpty())
        assertTrue(cryptoFactoryProviderCaptor.firstValue.publicKeys.isEmpty())
        assertTrue(cryptoFactoryProviderCaptor.firstValue.rpc.isEmpty())
    }

    @Test
    @Timeout(5)
    fun `Should close all AutoCloseable subcomponents`() {
        val cut = setupCoordinator()
        coordinator.postEvent(
            RegistrationStatusChangeEvent(
                registration = mock(),
                status = LifecycleStatus.UP
            )
        )
        Mockito.verify(configurationReadService, times(1)).registerForUpdates(any())
        cut.close()
        assertFalse(cut.isRunning)
        Mockito.verify(registrationHandle, times(1)).close()
        Mockito.verify(cipherSuiteFactory, times(1)).close()
        Mockito.verify(cryptoFactoryProvider, times(1)).close()
    }

    private fun setupCoordinator(): CryptoLibraryCoordinator {
        val cut = CryptoLibraryCoordinatorStub(
            coordinatorFactory,
            configurationReadService,
            cipherSuiteFactory,
            cryptoFactoryProvider
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

    private interface CipherSuiteFactoryStub : CipherSuiteFactory, Lifecycle, CryptoLifecycleComponent

    @Suppress("LongParameterList")
    private class CryptoLibraryCoordinatorStub(
        coordinatorFactory: LifecycleCoordinatorFactory,
        configurationReadService: ConfigurationReadService,
        cipherSuiteFactory: CipherSuiteFactory,
        cryptoFactoryProvider: CryptoLibraryClientsFactoryProviderImpl
    ) : CryptoLibraryCoordinator(
        coordinatorFactory,
        configurationReadService,
        cipherSuiteFactory,
        cryptoFactoryProvider
    ) {
        fun callHandleEvent(event: LifecycleEvent) =
            handleEvent(event)
    }
}