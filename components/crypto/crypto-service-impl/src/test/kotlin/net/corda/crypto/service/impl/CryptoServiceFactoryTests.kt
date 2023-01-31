package net.corda.crypto.service.impl

import net.corda.crypto.cipher.suite.ConfigurationSecrets
import net.corda.crypto.cipher.suite.CryptoService
import net.corda.crypto.component.impl.CryptoServiceProvider
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.InvalidParamsException
import net.corda.crypto.service.impl.infra.TestServicesFactory
import net.corda.crypto.softhsm.SoftCryptoServiceConfig
import net.corda.lifecycle.LifecycleStatus
import net.corda.test.util.eventually
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CryptoServiceFactoryTests {
    private lateinit var tenantId1: String
    private lateinit var tenantId2: String
    private lateinit var factory: TestServicesFactory
    private lateinit var component: CryptoServiceFactoryImpl

    @BeforeEach
    fun setup() {
        tenantId1 = UUID.randomUUID().toString()
        tenantId2 = UUID.randomUUID().toString()
        factory = TestServicesFactory()
        component = CryptoServiceFactoryImpl(
            factory.coordinatorFactory,
            factory.configurationReadService,
            factory.hsmService,
            object : CryptoServiceProvider<SoftCryptoServiceConfig> {
                override val name: String = CryptoConsts.SOFT_HSM_SERVICE_NAME
                override val configType: Class<SoftCryptoServiceConfig> = SoftCryptoServiceConfig::class.java
                override fun getInstance(
                    config: SoftCryptoServiceConfig,
                    secrets: ConfigurationSecrets
                ): CryptoService = factory.cryptoService
            }
        )
        factory.hsmService.assignSoftHSM(tenantId1, CryptoConsts.Categories.LEDGER)
        factory.hsmService.assignSoftHSM(tenantId1, CryptoConsts.Categories.TLS)
        factory.hsmService.assignSoftHSM(tenantId2, CryptoConsts.Categories.TLS)
    }

    @Test
    fun `Should start component and use active implementation only after the component is up`() {
        assertFalse(component.isRunning)
        assertThrows<IllegalStateException> {
            component.impl
        }
        component.start()
        component.bootstrapConfig(factory.bootstrapConfig)
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertNotNull(component.impl)
    }

    @Test
    fun `Should deactivate implementation when component is stopped`() {
        assertFalse(component.isRunning)
        component.start()
        component.bootstrapConfig(factory.bootstrapConfig)
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        component.stop()
        eventually {
            assertFalse(component.isRunning)
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertThrows<IllegalStateException> {
            component.impl
        }
    }

    @Test
    fun `Should go UP and DOWN as its upstream dependencies go UP and DOWN`() {
        assertFalse(component.isRunning)
        component.start()
        component.bootstrapConfig(factory.bootstrapConfig)
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        factory.hsmService.lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
        eventually {
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertThrows<IllegalStateException> {
            component.impl
        }
        factory.hsmService.lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertNotNull(component.impl)
    }

    @Test
    fun `findInstance(tenant,category) should return same instance`() {
        assertFalse(component.isRunning)
        component.start()
        component.bootstrapConfig(factory.bootstrapConfig)
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val i1 = component.findInstance(tenantId1, CryptoConsts.Categories.LEDGER)
        val i2 = component.findInstance(tenantId1, CryptoConsts.Categories.TLS)
        val i3 = component.findInstance(tenantId2, CryptoConsts.Categories.TLS)
        assertNotNull(i1)
        assertNotNull(i2)
        assertNotNull(i3)
        assertEquals(tenantId1, i1.tenantId)
        assertEquals(CryptoConsts.Categories.LEDGER, i1.category)
        assertEquals(tenantId1, i2.tenantId)
        assertEquals(CryptoConsts.Categories.TLS, i2.category)
        assertEquals(tenantId2, i3.tenantId)
        assertEquals(CryptoConsts.Categories.TLS, i3.category)
        assertSame(i1.instance, i2.instance)
        assertSame(i1.instance, i3.instance)
    }

    @Test
    fun `findInstance(tenant,category) should fail when this component not handling the HSM`() {
        assertFalse(component.isRunning)
        component.start()
        component.bootstrapConfig(factory.bootstrapConfig)
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertThrows<InvalidParamsException> {
            component.findInstance(tenantId2, CryptoConsts.Categories.LEDGER)
        }
        assertThrows<InvalidParamsException> {
            component.findInstance(UUID.randomUUID().toString(), CryptoConsts.Categories.LEDGER)
        }
    }

    @Test
    fun `getInstance(configId) should return same instance each time`() {
        assertFalse(component.isRunning)
        assertThrows<IllegalStateException> {
            component.impl
        }
        component.start()
        component.bootstrapConfig(factory.bootstrapConfig)
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val i1 = component.getInstance(CryptoConsts.SOFT_HSM_ID)
        val i2 = component.getInstance(CryptoConsts.SOFT_HSM_ID)
        assertNotNull(i1)
        assertSame(i1, i2)
    }

    @Test
    fun `getInstance(configId) should fail when hsm is not handled by this secomponentrvice`() {
        assertFalse(component.isRunning)
        assertThrows<IllegalStateException> {
            component.impl
        }
        component.start()
        component.bootstrapConfig(factory.bootstrapConfig)
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertNotNull(component.getInstance(CryptoConsts.SOFT_HSM_ID))
        assertThrows<IllegalArgumentException> {
            component.getInstance(UUID.randomUUID().toString())
        }
    }
}