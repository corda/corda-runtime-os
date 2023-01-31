package net.corda.crypto.softhsm.impl

import com.typesafe.config.ConfigFactory
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.softhsm.KEY_MAP_CACHING_NAME
import net.corda.crypto.softhsm.KEY_MAP_TRANSIENT_NAME
import net.corda.crypto.softhsm.SoftCryptoServiceProvider
import net.corda.crypto.softhsm.WRAPPING_DEFAULT_NAME
import net.corda.crypto.softhsm.impl.infra.TestWrappingKeyStore
import net.corda.lifecycle.LifecycleCoordinatorName
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
    private lateinit var defaultConfig: Config

    @BeforeEach
    fun setup() {
        defaultConfig = createCustomConfig(KEY_MAP_CACHING_NAME, KEY_MAP_CACHING_NAME)
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
    fun `Should return expected lifecycle coordinator name`() {
        assertEquals(
            LifecycleCoordinatorName.forComponent<SoftCryptoServiceProvider>(),
            component.lifecycleName
        )
    }

    @Test
    fun `Should start component and use active implementation only after the component is up`() {
        assertFalse(component.isRunning)
        assertThrows<IllegalStateException> {
            component.getInstance(defaultConfig)
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertNotNull(component.getInstance(defaultConfig))
    }

    @Test
    fun `getInstance should return new instance with default config each time`() {
        assertFalse(component.isRunning)
        assertThrows<IllegalStateException> {
            component.getInstance(defaultConfig)
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val i1 = component.getInstance(defaultConfig)
        val i2 = component.getInstance(defaultConfig)
        assertNotNull(i1)
        assertNotNull(i2)
        assertNotSame(i1, i2)
    }

    private fun createCustomConfig(keyMapName: String, wrappingKeyMapName: String) = ConfigFactory.parseString(
        """
            {
                "keyMap": {
                    "name" : "$keyMapName",
                    "cache": {
                        "expireAfterAccessMins" : 60,
                        "maximumSize" : 1000
                    }
                },
                "wrappingKeyMap": {
                    "name" : "$wrappingKeyMapName",
                    "salt" : "salt",
                    "passphrase" : "passphrase",
                    "cache" : {
                        "expireAfterAccessMins" : 60,
                        "maximumSize" : 1000
                    }
                }
                "wrapping" : {
                    "name" : "$WRAPPING_DEFAULT_NAME",
                }
            }
        """
    )

    @Test
    fun `getInstance should return new instance with custom config each time`() {
        val customConfig = createCustomConfig(KEY_MAP_TRANSIENT_NAME, KEY_MAP_TRANSIENT_NAME)
        assertFalse(component.isRunning)
        assertThrows<IllegalStateException> {
            component.getInstance(customConfig)
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val i1 = component.getInstance(customConfig)
        val i2 = component.getInstance(customConfig)
        assertNotNull(i1)
        assertNotNull(i2)
        assertNotSame(i1, i2)
    }

    @Test
    fun `getInstance should throw IllegalStateException when creating new instance with unknown soft key map`() {
        val customConfig = createCustomConfig("<unknown name>", KEY_MAP_TRANSIENT_NAME)
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertThrows<IllegalStateException> {
            component.getInstance(customConfig)
        }
    }

    @Test
    fun `getInstance should throw IllegalStateException when creating new instance with unknown wrapping key map`() {
        val customConfig = createCustomConfig(KEY_MAP_TRANSIENT_NAME, "<unknown name>")
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertThrows<IllegalStateException> {
            component.getInstance(customConfig)
        }
    }

    @Test
    fun `Should deactivate implementation when component is stopped`() {
        assertFalse(component.isRunning)
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertNotNull(component.getInstance(defaultConfig))
        component.stop()
        eventually {
            assertFalse(component.isRunning)
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        eventually {
            assertThrows<IllegalStateException> {
                component.getInstance(defaultConfig)
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
        assertNotNull(component.getInstance(defaultConfig))
        wrappingKeyStore.lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
        eventually {
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertThrows<IllegalStateException> {
            component.getInstance(defaultConfig)
        }
        wrappingKeyStore.lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertNotNull(component.getInstance(defaultConfig))
    }
}