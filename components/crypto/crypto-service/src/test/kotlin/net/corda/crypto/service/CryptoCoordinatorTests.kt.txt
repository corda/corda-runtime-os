package net.corda.crypto.service

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.component.lifecycle.AbstractCryptoCoordinator
import net.corda.crypto.impl.DefaultCryptoServiceProvider
import net.corda.crypto.service.rpc.CryptoRpcSub
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.lifecycle.CryptoLifecycleComponent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CryptoCoordinatorTests {
    private lateinit var coordinator: LifecycleCoordinator
    private lateinit var coordinatorFactory: LifecycleCoordinatorFactory
    private lateinit var configurationReadService: ConfigurationReadService
    private lateinit var cipherSuiteFactory: CipherSuiteFactoryStub
    private lateinit var cryptoFactory: CryptoFactoryStub
    private lateinit var configChangeHandler: ConfigurationHandler
    private lateinit var defaultCryptoServiceProvider: DefaultCryptoServiceProvider
    private lateinit var sub1: CryptoRpcSubStub
    private lateinit var sub2: CryptoRpcSubStub
    private lateinit var registrationHandle: AutoCloseable
    private var coordinatorIsRunning = false
    private val configFactory = SmartConfigFactory.create(ConfigFactory.empty())

    @BeforeEach
    fun setup() {
        coordinator = mock()
        coordinatorFactory = mock()
        configurationReadService = mock()
        cipherSuiteFactory = mock()
        cryptoFactory = mock()
        defaultCryptoServiceProvider = mock()
        sub1 = mock()
        sub2 = mock()
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
    fun `Should throw IllegalStateException when config change for crypto library is empty`() {
        setupCoordinator()
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
        Mockito.verify(cipherSuiteFactory, never()).start()
        Mockito.verify(cipherSuiteFactory as CryptoLifecycleComponent, never()).handleConfigEvent(any())
        Mockito.verify(cryptoFactory, never()).start()
        Mockito.verify(cryptoFactory as CryptoLifecycleComponent, never()).handleConfigEvent(any())
        Mockito.verify(defaultCryptoServiceProvider, never()).start()
        Mockito.verify(defaultCryptoServiceProvider as CryptoLifecycleComponent, never()).handleConfigEvent(any())
        Mockito.verify(sub1 as Lifecycle, never()).start()
        Mockito.verify(sub1 as CryptoLifecycleComponent, never()).handleConfigEvent(any())
        Mockito.verify(sub2 as Lifecycle, never()).start()
        Mockito.verify(sub2 as CryptoLifecycleComponent, never()).handleConfigEvent(any())
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
        Mockito.verify(cryptoFactory, times(1)).close()
        Mockito.verify(defaultCryptoServiceProvider, times(1)).close()
        Mockito.verify(sub1 as AutoCloseable, times(1)).close()
        Mockito.verify(sub2 as AutoCloseable, times(1)).close()
    }

    private fun setupCoordinator(): CryptoCoordinator {
        val cut = CryptoCoordinatorStub(
            coordinatorFactory,
            configurationReadService,
            cipherSuiteFactory,
            cryptoFactory,
            defaultCryptoServiceProvider,
            listOf(
                sub1,
                sub2
            )
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

    private interface CipherSuiteFactoryStub: CipherSuiteFactory, Lifecycle, CryptoLifecycleComponent

    private interface CryptoFactoryStub: CryptoFactory, Lifecycle, CryptoLifecycleComponent

    private interface CryptoRpcSubStub: CryptoRpcSub, Lifecycle

    @Suppress("LongParameterList")
    private class CryptoCoordinatorStub(
        coordinatorFactory: LifecycleCoordinatorFactory,
        configurationReadService: ConfigurationReadService,
        cipherSuiteFactory: CipherSuiteFactory,
        cryptoFactory: CryptoFactory,
        defaultCryptoServiceProvider: DefaultCryptoServiceProvider,
        rpcSubs: List<CryptoRpcSub>
    ) : CryptoCoordinator(
        coordinatorFactory,
        configurationReadService,
        cipherSuiteFactory,
        cryptoFactory,
        defaultCryptoServiceProvider,
        rpcSubs
    ) {
        fun callHandleEvent(event: LifecycleEvent) =
            handleEvent(event)
    }
}