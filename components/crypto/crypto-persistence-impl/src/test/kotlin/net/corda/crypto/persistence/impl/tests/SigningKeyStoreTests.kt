package net.corda.crypto.persistence.impl.tests

import java.security.PublicKey
import java.util.UUID
import net.corda.crypto.component.test.utils.TestConfigurationReadService
import net.corda.crypto.config.impl.createDefaultCryptoConfig
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.publicKeyIdFromBytes
import net.corda.crypto.persistence.impl.SigningKeyStoreImpl
import net.corda.crypto.persistence.impl.tests.infra.TestCryptoLifecycle
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.test.impl.TestLifecycleCoordinatorFactoryImpl
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.test.util.eventually
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SigningKeyStoreTests {
    private lateinit var configurationReadService: TestConfigurationReadService
    private lateinit var dbConnectionManagerLifecycle: TestCryptoLifecycle
    private lateinit var virtualNodeInfoReadServiceLifecycle: TestCryptoLifecycle
    private lateinit var coordinatorFactory: LifecycleCoordinatorFactory
    private lateinit var component: SigningKeyStoreImpl

    fun createLifecycle(name: LifecycleCoordinatorName) =
        TestCryptoLifecycle(
            coordinatorFactory,
            name
        ).also {
            it.start()
            eventually {
                assertEquals(LifecycleStatus.UP, it.lifecycleCoordinator.status)
            }
        }

    @BeforeEach
    fun setup() {
        coordinatorFactory = TestLifecycleCoordinatorFactoryImpl()

        configurationReadService = TestConfigurationReadService(
            coordinatorFactory,
            configUpdates = listOf(
                CRYPTO_CONFIG to SmartConfigFactory.createWithoutSecurityServices()
                    .create(createDefaultCryptoConfig("pass", "salt"))
            )
        ).also {
            it.start()
            eventually {
                assertEquals(LifecycleStatus.UP, it.lifecycleCoordinator.status)
            }
        }
        dbConnectionManagerLifecycle = createLifecycle(LifecycleCoordinatorName.forComponent<DbConnectionManager>())
        virtualNodeInfoReadServiceLifecycle = createLifecycle(LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>())

        component = SigningKeyStoreImpl(
            coordinatorFactory,
            configurationReadService,
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
        )
    }

    @Test
    fun `Should create ActiveImpl only after the component is up`() {
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
    fun `Should use InactiveImpl when component is stopped`() {
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
        dbConnectionManagerLifecycle.lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
        eventually {
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertThrows<IllegalStateException> { component.impl }
        dbConnectionManagerLifecycle.lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertNotNull(component.impl)
    }

    @Test
    fun `Should throw IllegalArgumentException when the lookup by ids keys is passed more than 20 items`() {
        assertFalse(component.isRunning)
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val keys = (0 until 21).map {
            publicKeyIdFromBytes(
                mock<PublicKey> {
                    on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
                }.encoded
            )
        }
        assertThrows<IllegalArgumentException> {
            component.lookupByIds(UUID.randomUUID().toString(), keys.map { ShortHash.of(it) })
        }
    }
}

