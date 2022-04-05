package net.corda.crypto.service.impl.signing

import net.corda.crypto.service.impl._utils.TestServicesFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.test.util.eventually
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SigningServiceFactoryTests {
    private lateinit var factory: TestServicesFactory
    private lateinit var component: SigningServiceFactoryImpl

    @BeforeEach
    fun setup() {
        factory = TestServicesFactory()
        component = SigningServiceFactoryImpl(
            factory.coordinatorFactory,
            factory.schemeMetadata,
            factory.signingCacheProvider,
            factory.createCryptoServiceFactory()
        )
    }

    @Test
    fun `Should start component and use active implementation only after the component is up`() {
        assertFalse(component.isRunning)
        assertInstanceOf(SigningServiceFactoryImpl.InactiveImpl::class.java, component.impl)
        assertThrows<IllegalStateException> {
            component.getInstance()
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(SigningServiceFactoryImpl.ActiveImpl::class.java, component.impl)
        assertNotNull(
            component.getInstance()
        )
    }

    @Test
    fun `getInstance should return same instance each time`() {
        assertFalse(component.isRunning)
        assertInstanceOf(SigningServiceFactoryImpl.InactiveImpl::class.java, component.impl)
        assertThrows<IllegalStateException> {
            component.getInstance()
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(SigningServiceFactoryImpl.ActiveImpl::class.java, component.impl)
        val i1 = component.getInstance()
        val i2 = component.getInstance()
        val i3 = component.getInstance()
        assertNotNull(i1)
        assertSame(i1, i2)
        assertSame(i1, i3)
    }

    @Test
    fun `Should deactivate implementation when component is stopped`() {
        assertFalse(component.isRunning)
        assertInstanceOf(SigningServiceFactoryImpl.InactiveImpl::class.java, component.impl)
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(SigningServiceFactoryImpl.ActiveImpl::class.java, component.impl)
        component.stop()
        eventually {
            assertFalse(component.isRunning)
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(SigningServiceFactoryImpl.InactiveImpl::class.java, component.impl)
    }
}