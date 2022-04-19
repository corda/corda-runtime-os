package net.corda.crypto.service.impl.soft

import net.corda.crypto.service.SoftCryptoServiceConfig
import net.corda.crypto.service.impl.infra.TestServicesFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.test.util.eventually
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class SoftCryptoServiceProviderTests {
    private lateinit var factory: TestServicesFactory
    private lateinit var component: SoftCryptoServiceProviderImpl

    @BeforeEach
    fun setup() {
        factory = TestServicesFactory()
        component = SoftCryptoServiceProviderImpl(
            factory.coordinatorFactory,
            factory.schemeMetadata,
            factory.digest,
            factory.softCacheProvider
        )
    }

    @Test
    fun `Should start component and use active implementation only after the component is up`() {
        assertFalse(component.isRunning)
        assertInstanceOf(SoftCryptoServiceProviderImpl.InactiveImpl::class.java, component.impl)
        assertThrows<IllegalStateException> {
            component.getInstance(
                SoftCryptoServiceConfig(
                    passphrase = "PASSPHRASE",
                    salt = "SALT"
                )
            )
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(SoftCryptoServiceProviderImpl.ActiveImpl::class.java, component.impl)
        assertNotNull(
            component.getInstance(
                SoftCryptoServiceConfig(
                    passphrase = "PASSPHRASE",
                    salt = "SALT"
                )
            )
        )
    }

    @Test
    fun `getInstance should return new instance each time`() {
        assertFalse(component.isRunning)
        assertInstanceOf(SoftCryptoServiceProviderImpl.InactiveImpl::class.java, component.impl)
        assertThrows<IllegalStateException> {
            component.getInstance(
                SoftCryptoServiceConfig(
                    passphrase = "PASSPHRASE",
                    salt = "SALT"
                )
            )
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(SoftCryptoServiceProviderImpl.ActiveImpl::class.java, component.impl)
        val i1 = component.getInstance(
                SoftCryptoServiceConfig(
                    passphrase = "PASSPHRASE",
                    salt = "SALT"
                )
            )
        val i2 = component.getInstance(
            SoftCryptoServiceConfig(
                passphrase = "PASSPHRASE",
                salt = "SALT"
            )
        )
        assertNotNull(i1)
        assertNotNull(i2)
        assertNotSame(i1, i2)
    }

    @Test
    fun `Should deactivate implementation when component is stopped`() {
        assertFalse(component.isRunning)
        assertInstanceOf(SoftCryptoServiceProviderImpl.InactiveImpl::class.java, component.impl)
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(SoftCryptoServiceProviderImpl.ActiveImpl::class.java, component.impl)
        component.stop()
        eventually {
            assertFalse(component.isRunning)
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        eventually {
            assertInstanceOf(SoftCryptoServiceProviderImpl.InactiveImpl::class.java, component.impl)
        }
    }

    @Test
    fun `Should go UP and DOWN as its dependencies go UP and DOWN`() {
        assertFalse(component.isRunning)
        assertInstanceOf(SoftCryptoServiceProviderImpl.InactiveImpl::class.java, component.impl)
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(SoftCryptoServiceProviderImpl.ActiveImpl::class.java, component.impl)
        factory.softCacheProvider.coordinator.updateStatus(LifecycleStatus.DOWN)
        eventually {
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(SoftCryptoServiceProviderImpl.InactiveImpl::class.java, component.impl)
        factory.softCacheProvider.coordinator.updateStatus(LifecycleStatus.UP)
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(SoftCryptoServiceProviderImpl.ActiveImpl::class.java, component.impl)
    }
}