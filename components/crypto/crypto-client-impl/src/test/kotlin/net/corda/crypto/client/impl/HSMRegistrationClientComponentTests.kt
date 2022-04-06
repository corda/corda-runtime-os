package net.corda.crypto.client.impl

import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.client.CryptoPublishResult
import net.corda.crypto.client.impl._utils.PublishActResult
import net.corda.crypto.client.impl._utils.TestConfigurationReadService
import net.corda.crypto.client.impl._utils.act
import net.corda.data.crypto.config.HSMConfig
import net.corda.data.crypto.config.HSMInfo
import net.corda.data.crypto.wire.registration.hsm.AddHSMCommand
import net.corda.data.crypto.wire.registration.hsm.AssignHSMCommand
import net.corda.data.crypto.wire.registration.hsm.AssignSoftHSMCommand
import net.corda.data.crypto.wire.registration.hsm.HSMRegistrationRequest
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.schema.Schemas
import net.corda.test.util.eventually
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.cipher.suite.schemes.EDDSA_ED25519_CODE_NAME
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.emptyOrNullString
import org.hamcrest.Matchers.not
import org.hamcrest.core.IsInstanceOf
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HSMRegistrationClientComponentTests {
    private lateinit var publisher: Publisher
    private lateinit var coordinatorFactory: LifecycleCoordinatorFactory
    private lateinit var configurationReadService: TestConfigurationReadService
    private lateinit var publisherFactory: PublisherFactory
    private lateinit var component: HSMRegistrationClientComponent

    @BeforeEach
    fun setup() {
        coordinatorFactory = LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl())
        publisher = mock {
            on { publish(any()) } doReturn listOf(CompletableFuture<Unit>().also { it.complete(Unit) })
        }
        publisherFactory = mock {
            on { createPublisher(any(), any()) } doReturn publisher
        }
        configurationReadService = TestConfigurationReadService(
            coordinatorFactory
        ).also {
            it.start()
            eventually {
                assertTrue(it.isRunning)
            }
        }
        component = HSMRegistrationClientComponent(
            coordinatorFactory = coordinatorFactory,
            publisherFactory = publisherFactory,
            configurationReadService = configurationReadService
        )
    }

    private fun assertRequestContext(
        result: PublishActResult<CryptoPublishResult>,
        expectedTenantId: String
    ): HSMRegistrationRequest {
        val req = result.firstRecord.value as HSMRegistrationRequest
        val context = req.context
        assertEquals(expectedTenantId, context.tenantId)
        assertEquals(result.value.requestId, context.requestId)
        result.assertThatIsBetween(context.requestTimestamp)
        assertEquals(HSMRegistrationClientImpl::class.simpleName, context.requestingComponent)
        assertThat(context.other.items, empty())
        return req
    }

    private inline fun <reified OP> assertOperationType(result: PublishActResult<CryptoPublishResult>): OP {
        val req = result.firstRecord.value as HSMRegistrationRequest
        Assertions.assertNotNull(req.request)
        assertThat(req.request, IsInstanceOf(OP::class.java))
        return req.request as OP
    }

    @Test
    fun `Should publish command to add HSM configuration`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val config = HSMConfig(
            HSMInfo(
                UUID.randomUUID().toString(),
                Instant.now(),
                7,
                "default",
                "Test HSM",
                "default",
                null,
                listOf(CryptoConsts.HsmCategories.LEDGER, CryptoConsts.HsmCategories.TLS),
                11,
                5500,
                listOf(ECDSA_SECP256K1_CODE_NAME, ECDSA_SECP256R1_CODE_NAME, EDDSA_ED25519_CODE_NAME)
            ),
            ByteBuffer.allocate(0)
        )
        val result = publisher.act {
            component.putHSM(config)
        }
        assertThat(result.value.requestId, not(emptyOrNullString()))
        assertEquals(1, result.messages.size)
        assertEquals(1, result.messages.first().size)
        assertEquals(Schemas.Crypto.HSM_REGISTRATION_MESSAGE_TOPIC, result.firstRecord.topic)
        assertEquals(CryptoConsts.CLUSTER_TENANT_ID, result.firstRecord.key)
        assertThat(result.firstRecord.value, IsInstanceOf(HSMRegistrationRequest::class.java))
        assertRequestContext(result, CryptoConsts.CLUSTER_TENANT_ID)
        val command = assertOperationType<AddHSMCommand>(result)
        assertSame(config, command.config)
        assertNotNull(command.context)
        assertNotNull(command.context.items)
        assertThat(command.context.items, empty())
    }

    @Test
    fun `Should publish command to assign HSM`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val result = publisher.act {
            component.assignHSM(
                tenantId = "some-tenant",
                category = CryptoConsts.HsmCategories.LEDGER,
                defaultSignatureScheme = EDDSA_ED25519_CODE_NAME
            )
        }
        assertThat(result.value.requestId, not(emptyOrNullString()))
        assertEquals(1, result.messages.size)
        assertEquals(1, result.messages.first().size)
        assertEquals(Schemas.Crypto.HSM_REGISTRATION_MESSAGE_TOPIC, result.firstRecord.topic)
        assertEquals(CryptoConsts.CLUSTER_TENANT_ID, result.firstRecord.key)
        assertThat(result.firstRecord.value, IsInstanceOf(HSMRegistrationRequest::class.java))
        val req = assertRequestContext(result, "some-tenant")
        assertEquals("some-tenant", req.context.tenantId)
        val command = assertOperationType<AssignHSMCommand>(result)
        assertEquals (CryptoConsts.HsmCategories.LEDGER, command.category)
        assertEquals(EDDSA_ED25519_CODE_NAME, command.defaultSignatureScheme)
        assertNotNull(command.context)
        assertNotNull(command.context.items)
        assertThat(command.context.items, empty())
    }

    @Test
    fun `Should publish command to assign Soft HSM`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val result = publisher.act {
            component.assignSoftHSM(
                tenantId = "some-tenant",
                category = CryptoConsts.HsmCategories.LEDGER,
                passphrase = "1234",
                defaultSignatureScheme = EDDSA_ED25519_CODE_NAME
            )
        }
        assertThat(result.value.requestId, not(emptyOrNullString()))
        assertEquals(1, result.messages.size)
        assertEquals(1, result.messages.first().size)
        assertEquals(Schemas.Crypto.HSM_REGISTRATION_MESSAGE_TOPIC, result.firstRecord.topic)
        assertEquals(CryptoConsts.CLUSTER_TENANT_ID, result.firstRecord.key)
        assertThat(result.firstRecord.value, IsInstanceOf(HSMRegistrationRequest::class.java))
        val req = assertRequestContext(result, "some-tenant")
        assertEquals("some-tenant", req.context.tenantId)
        val command = assertOperationType<AssignSoftHSMCommand>(result)
        assertEquals (CryptoConsts.HsmCategories.LEDGER, command.category)
        assertEquals ("1234", command.passphrase)
        assertEquals(EDDSA_ED25519_CODE_NAME, command.defaultSignatureScheme)
        assertNotNull(command.context)
        assertNotNull(command.context.items)
        assertThat(command.context.items, empty())
    }

    @Test
    fun `Should create active implementation only after the component is up`() {
        assertFalse(component.isRunning)
        assertInstanceOf(HSMRegistrationClientComponent.InactiveImpl::class.java, component.impl)
        assertThrows<IllegalStateException> {
            component.impl.registrar
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(HSMRegistrationClientComponent.ActiveImpl::class.java, component.impl)
        assertNotNull(component.impl.registrar)
    }

    @Test
    fun `Should cleanup created resources when component is stopped`() {
        assertFalse(component.isRunning)
        assertInstanceOf(HSMRegistrationClientComponent.InactiveImpl::class.java, component.impl)
        assertThrows<IllegalStateException> {
            component.impl.registrar
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(HSMRegistrationClientComponent.ActiveImpl::class.java, component.impl)
        assertNotNull(component.impl.registrar)
        component.stop()
        eventually {
            assertFalse(component.isRunning)
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(HSMRegistrationClientComponent.InactiveImpl::class.java, component.impl)
        Mockito.verify(publisher, times(1)).close()
    }

    @Test
    fun `Should go UP and DOWN as its dependencies go UP and DOWN`() {
        assertFalse(component.isRunning)
        assertInstanceOf(HSMRegistrationClientComponent.InactiveImpl::class.java, component.impl)
        assertThrows<IllegalStateException> {
            component.impl.registrar
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(HSMRegistrationClientComponent.ActiveImpl::class.java, component.impl)
        assertNotNull(component.impl.registrar)
        configurationReadService.coordinator.updateStatus(LifecycleStatus.DOWN)
        eventually {
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(HSMRegistrationClientComponent.InactiveImpl::class.java, component.impl)
        configurationReadService.coordinator.updateStatus(LifecycleStatus.UP)
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(HSMRegistrationClientComponent.ActiveImpl::class.java, component.impl)
        assertNotNull(component.impl.registrar)
        Mockito.verify(publisher, atLeast(1)).close()
    }
}