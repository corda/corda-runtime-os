package net.corda.crypto.softhsm.impl

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.softhsm.SoftCacheConfig
import net.corda.crypto.softhsm.KEY_MAP_CACHING_NAME
import net.corda.crypto.softhsm.SoftKeyMapConfig
import net.corda.crypto.softhsm.SoftCryptoServiceConfig
import net.corda.crypto.softhsm.WRAPPING_DEFAULT_NAME
import net.corda.crypto.softhsm.SoftWrappingConfig
import net.corda.crypto.softhsm.SoftWrappingKeyMapConfig
import net.corda.crypto.softhsm.impl.infra.TestWrappingKeyStore
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.test.impl.TestLifecycleCoordinatorFactoryImpl
import net.corda.test.util.eventually
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class SoftCryptoServiceProviderTests {
    private lateinit var coordinatorFactory: TestLifecycleCoordinatorFactoryImpl
    private lateinit var schemeMetadata: CipherSchemeMetadataImpl
    private lateinit var wrappingKeyStore: TestWrappingKeyStore
    private lateinit var component: SoftCryptoServiceProviderImpl
    private lateinit var config: SoftCryptoServiceConfig

    @BeforeEach
    fun setup() {
        config = SoftCryptoServiceConfig(
            keyMap = SoftKeyMapConfig(
                name = KEY_MAP_CACHING_NAME,
                cache = SoftCacheConfig(
                    expireAfterAccessMins = 60,
                    maximumSize = 1000
                )
            ),
            wrappingKeyMap = SoftWrappingKeyMapConfig(
                name = KEY_MAP_CACHING_NAME,
                salt = "salt",
                passphrase = "passphrase",
                cache = SoftCacheConfig(
                    expireAfterAccessMins = 60,
                    maximumSize = 1000
                )
            ),
            wrapping = SoftWrappingConfig(
                name = WRAPPING_DEFAULT_NAME
            )
        )
        coordinatorFactory = TestLifecycleCoordinatorFactoryImpl()
        schemeMetadata = CipherSchemeMetadataImpl()
        wrappingKeyStore = TestWrappingKeyStore(coordinatorFactory).also {
            it.start()
            eventually {
                assertEquals(LifecycleStatus.UP, it.lifecycleCoordinator.status)
            }
        }
        component = SoftCryptoServiceProviderImpl(
            coordinatorFactory,
            schemeMetadata,
            mock(),
            wrappingKeyStore
        )
    }

    @Test
    fun `Should start component and use active implementation only after the component is up`() {
        assertFalse(component.isRunning)
        assertThrows<IllegalStateException> {
            component.getInstance(config)
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertNotNull(component.getInstance(config))
    }

    @Test
    fun `getInstance should return new instance each time`() {
        assertFalse(component.isRunning)
        assertThrows<IllegalStateException> {
            component.getInstance(config)
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val i1 = component.getInstance(config)
        val i2 = component.getInstance(config)
        assertNotNull(i1)
        assertNotNull(i2)
        assertNotSame(i1, i2)
    }

    @Test
    fun `Should deactivate implementation when component is stopped`() {
        assertFalse(component.isRunning)
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertNotNull(component.getInstance(config))
        component.stop()
        eventually {
            assertFalse(component.isRunning)
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        eventually {
            assertThrows<IllegalStateException> {
                component.getInstance(config)
            }
        }
    }

    @Test
    fun `Should go UP and DOWN as its dependencies go UP and DOWN`() {
        assertFalse(component.isRunning)
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertNotNull(component.getInstance(config))
        wrappingKeyStore.lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
        eventually {
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertThrows<IllegalStateException> {
            component.getInstance(config)
        }
        wrappingKeyStore.lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertNotNull(component.getInstance(config))
    }
}