package net.corda.crypto.hes.impl

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.hes.impl.infra.TestCryptoOpsClient
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.test.impl.TestLifecycleCoordinatorFactoryImpl
import net.corda.test.util.eventually
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StableKeyPairDecryptorTests {
    private lateinit var coordinatorFactory: TestLifecycleCoordinatorFactoryImpl
    private lateinit var schemeMetadata: CipherSchemeMetadata
    private lateinit var cryptoOpsClient: TestCryptoOpsClient
    private lateinit var component: StableKeyPairDecryptorImpl

    @BeforeEach
    fun setup() {
        coordinatorFactory = TestLifecycleCoordinatorFactoryImpl()
        schemeMetadata = CipherSchemeMetadataImpl()
        cryptoOpsClient = TestCryptoOpsClient(coordinatorFactory, mock()).also {
            it.start()
            eventually {
                assertEquals(LifecycleStatus.UP, it.lifecycleCoordinator.status)
            }
        }
        component = StableKeyPairDecryptorImpl(coordinatorFactory, schemeMetadata, cryptoOpsClient)
    }

    @Test
    fun `Should start component and use active implementation only after the component is up`() {
        assertFalse(component.isRunning)
        assertThrows<IllegalStateException> { component.impl }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertNotNull(component.impl)
    }

    @Test
    fun `Should deactivate implementation when component is stopped`() {
        assertFalse(component.isRunning)
        assertThrows<IllegalStateException> { component.impl }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertNotNull(component.impl)
        component.stop()
        eventually {
            assertFalse(component.isRunning)
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertThrows<IllegalStateException> { component.impl }
    }

    @Test
    fun `Should go UP and DOWN as its dependencies go UP and DOWN`() {
        assertFalse(component.isRunning)
        assertThrows<IllegalStateException> { component.impl }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertNotNull(component.impl)
        cryptoOpsClient.lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
        eventually {
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertThrows<IllegalStateException> { component.impl }
        cryptoOpsClient.lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertNotNull(component.impl)
    }
}