package net.corda.crypto.service.impl.signing

import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.service.impl.hsm.soft.SoftCryptoService
import net.corda.crypto.service.impl.infra.TestServicesFactory
import net.corda.data.crypto.wire.hsm.HSMCategoryInfo
import net.corda.data.crypto.wire.hsm.HSMInfo
import net.corda.data.crypto.wire.hsm.MasterKeyPolicy
import net.corda.data.crypto.wire.hsm.PrivateKeyPolicy
import net.corda.lifecycle.LifecycleStatus
import net.corda.test.util.eventually
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CryptoServiceFactoryTests {
    private lateinit var tenantId: String
    private lateinit var tenantId2: String
    private lateinit var tenantId3: String
    private lateinit var tenantId4: String
    private lateinit var customHSMConfigId: String
    private lateinit var factory: TestServicesFactory
    private lateinit var component: CryptoServiceFactoryImpl

    @BeforeEach
    fun setup() {
        tenantId = UUID.randomUUID().toString()
        tenantId2 = UUID.randomUUID().toString()
        tenantId3 = UUID.randomUUID().toString()
        tenantId4 = UUID.randomUUID().toString()
        factory = TestServicesFactory()
        component = CryptoServiceFactoryImpl(
            factory.coordinatorFactory,
            factory.readService,
            factory.hsmService,
            listOf(
                factory.softCryptoKeyCacheProvider
            )
        )
        factory.hsmService.assignSoftHSM(tenantId, CryptoConsts.Categories.LEDGER)
        factory.hsmService.assignSoftHSM(tenantId, CryptoConsts.Categories.TLS)
        factory.hsmService.assignSoftHSM(tenantId2, CryptoConsts.Categories.LEDGER)
        factory.hsmService.assignSoftHSM(tenantId3, CryptoConsts.Categories.LEDGER)
        customHSMConfigId = factory.hsmService.putHSMConfig(
            HSMInfo(
                "",
                Instant.now(),
                null,
                "Some HSM configuration",
                MasterKeyPolicy.NEW,
                null,
                0,
                5000,
                SoftCryptoService.produceSupportedSchemes(factory.schemeMetadata).map { it.codeName },
                CryptoConsts.SOFT_HSM_SERVICE_NAME,
                -1
            ),
            "{}".toByteArray()
        )
        factory.hsmService.linkCategories(customHSMConfigId, listOf(HSMCategoryInfo(
            CryptoConsts.Categories.JWT_KEY,
            PrivateKeyPolicy.WRAPPED
        )))
        factory.hsmService.assignHSM(tenantId4, CryptoConsts.Categories.JWT_KEY, emptyMap())
    }

    @Test
    fun `Should start component and use active implementation only after the component is up`() {
        assertFalse(component.isRunning)
        assertInstanceOf(CryptoServiceFactoryImpl.InactiveImpl::class.java, component.impl)
        assertThrows<IllegalStateException> {
            component.getInstance(tenantId, CryptoConsts.Categories.LEDGER)
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(CryptoServiceFactoryImpl.ActiveImpl::class.java, component.impl)
        assertNotNull(
            component.getInstance(tenantId, CryptoConsts.Categories.LEDGER)
        )
    }

    @Test
    fun `getInstance(tenant,category) should return same instance when params are resolved to same HSM config id`() {
        assertFalse(component.isRunning)
        assertInstanceOf(CryptoServiceFactoryImpl.InactiveImpl::class.java, component.impl)
        assertThrows<IllegalStateException> {
            component.getInstance(tenantId, CryptoConsts.Categories.LEDGER)
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(CryptoServiceFactoryImpl.ActiveImpl::class.java, component.impl)
        val i1 = component.getInstance(tenantId, CryptoConsts.Categories.LEDGER)
        val i2 = component.getInstance(tenantId2, CryptoConsts.Categories.LEDGER)
        val i3 = component.getInstance(tenantId3, CryptoConsts.Categories.LEDGER)
        assertNotNull(i1)
        assertNotNull(i2)
        assertNotNull(i3)
        assertEquals(tenantId, i1.tenantId)
        assertEquals(CryptoConsts.Categories.LEDGER, i1.category)
        assertEquals(tenantId2, i2.tenantId)
        assertEquals(CryptoConsts.Categories.LEDGER, i2.category)
        assertEquals(tenantId3, i3.tenantId)
        assertEquals(CryptoConsts.Categories.LEDGER, i3.category)
        assertSame(i1.instance, i2.instance)
        assertSame(i1.instance, i3.instance)
    }

    @Test
    @Suppress("MaxLineLength")
    fun `getInstance(tenant,category) should return different instance when params are not resolved to same HSM config id`() {
        assertFalse(component.isRunning)
        assertInstanceOf(CryptoServiceFactoryImpl.InactiveImpl::class.java, component.impl)
        assertThrows<IllegalStateException> {
            component.getInstance(tenantId, CryptoConsts.Categories.LEDGER)
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(CryptoServiceFactoryImpl.ActiveImpl::class.java, component.impl)
        val i1 = component.getInstance(tenantId, CryptoConsts.Categories.LEDGER)
        val i2 = component.getInstance(tenantId4, CryptoConsts.Categories.JWT_KEY)
        assertNotNull(i1)
        assertNotNull(i2)
        assertEquals(tenantId, i1.tenantId)
        assertEquals(CryptoConsts.Categories.LEDGER, i1.category)
        assertEquals(tenantId4, i2.tenantId)
        assertEquals(CryptoConsts.Categories.JWT_KEY, i2.category)
        assertNotSame(i1.instance, i2.instance)
    }

    @Test
    fun `getInstance(configId) should return different instances for different HSM config id`() {
        assertFalse(component.isRunning)
        assertInstanceOf(CryptoServiceFactoryImpl.InactiveImpl::class.java, component.impl)
        assertThrows<IllegalStateException> {
            component.getInstance(tenantId, CryptoConsts.Categories.LEDGER)
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(CryptoServiceFactoryImpl.ActiveImpl::class.java, component.impl)
        val i1 = component.getInstance(CryptoConsts.SOFT_HSM_CONFIG_ID)
        val i2 = component.getInstance(customHSMConfigId)
        assertNotNull(i1)
        assertNotNull(i2)
        assertNotSame(i1, i2)
    }

    @Test
    fun `Should deactivate implementation when component is stopped`() {
        assertFalse(component.isRunning)
        assertInstanceOf(CryptoServiceFactoryImpl.InactiveImpl::class.java, component.impl)
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(CryptoServiceFactoryImpl.ActiveImpl::class.java, component.impl)
        component.stop()
        eventually {
            assertFalse(component.isRunning)
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(CryptoServiceFactoryImpl.InactiveImpl::class.java, component.impl)
    }

    @Test
    fun `Should go UP and DOWN as its dependencies go UP and DOWN`() {
        assertFalse(component.isRunning)
        assertInstanceOf(CryptoServiceFactoryImpl.InactiveImpl::class.java, component.impl)
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(CryptoServiceFactoryImpl.ActiveImpl::class.java, component.impl)
        factory.hsmService.coordinator.updateStatus(LifecycleStatus.DOWN)
        eventually {
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(CryptoServiceFactoryImpl.InactiveImpl::class.java, component.impl)
        factory.hsmService.coordinator.updateStatus(LifecycleStatus.UP)
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(CryptoServiceFactoryImpl.ActiveImpl::class.java, component.impl)
    }
}