package net.corda.crypto.client.impl

import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.client.impl.infra.SendActResult
import net.corda.crypto.client.impl.infra.TestConfigurationReadService
import net.corda.crypto.client.impl.infra.act
import net.corda.crypto.core.CryptoConsts.HSMContext.NOT_FAIL_IF_ASSOCIATION_EXISTS
import net.corda.crypto.core.CryptoConsts.HSMContext.PREFERRED_PRIVATE_KEY_POLICY_KEY
import net.corda.crypto.core.CryptoConsts.HSMContext.PREFERRED_PRIVATE_KEY_POLICY_NONE
import net.corda.data.KeyValuePair
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.hsm.HSMInfo
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationRequest
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationResponse
import net.corda.data.crypto.wire.hsm.registration.commands.AssignHSMCommand
import net.corda.data.crypto.wire.hsm.registration.commands.AssignSoftHSMCommand
import net.corda.data.crypto.wire.hsm.registration.queries.AssignedHSMQuery
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.test.util.eventually
import net.corda.v5.base.util.toHex
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import net.corda.v5.crypto.sha256Bytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HSMRegistrationClientComponentTests {
    private lateinit var knownTenantId: String
    private lateinit var sender: RPCSender<HSMRegistrationRequest, HSMRegistrationResponse>
    private lateinit var coordinatorFactory: LifecycleCoordinatorFactory
    private lateinit var configurationReadService: TestConfigurationReadService
    private lateinit var publisherFactory: PublisherFactory
    private lateinit var component: HSMRegistrationClientComponent

    @BeforeEach
    fun setup() {
        knownTenantId = UUID.randomUUID().toString().toByteArray().sha256Bytes().toHex().take(12)
        coordinatorFactory = LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl())
        sender = mock()
        publisherFactory = mock {
            on { createRPCSender<HSMRegistrationRequest, HSMRegistrationResponse>(any(), any()) } doReturn sender
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

    private fun setupCompletedResponse(respFactory: (HSMRegistrationRequest) -> Any) {
        whenever(
            sender.sendRequest(any())
        ).then {
            val req = it.getArgument(0, HSMRegistrationRequest::class.java)
            val future = CompletableFuture<HSMRegistrationResponse>()
            future.complete(
                HSMRegistrationResponse(
                    CryptoResponseContext(
                        req.context.requestingComponent,
                        req.context.requestTimestamp,
                        UUID.randomUUID().toString(),
                        Instant.now(),
                        req.context.tenantId,
                        req.context.other
                    ), respFactory(req)
                )
            )
            future
        }
    }

    private fun assertRequestContext(result: SendActResult<HSMRegistrationRequest, *>) {
        val context = result.firstRequest.context
        assertEquals(knownTenantId, context.tenantId)
        result.assertThatIsBetween(context.requestTimestamp)
        assertEquals(HSMRegistrationClientImpl::class.simpleName, context.requestingComponent)
        assertThat(context.other.items).isEmpty()
    }

    private inline fun <reified OP> assertOperationType(result: SendActResult<HSMRegistrationRequest, *>): OP {
        Assertions.assertNotNull(result.firstRequest.request)
        assertThat(result.firstRequest.request).isInstanceOf(OP::class.java)
        return result.firstRequest.request as OP
    }

    @Test
    fun `Should assign HSM`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val response = HSMInfo()
        setupCompletedResponse {
            response
        }
        val result = sender.act {
            component.assignHSM(
                tenantId = knownTenantId,
                category = CryptoConsts.Categories.LEDGER,
                context = mapOf(
                    PREFERRED_PRIVATE_KEY_POLICY_KEY to PREFERRED_PRIVATE_KEY_POLICY_NONE
                )
            )
        }
        assertSame(response, result.value)
        val command = assertOperationType<AssignHSMCommand>(result)
        assertEquals(CryptoConsts.Categories.LEDGER, command.category)
        assertThat(command.context.items).hasSize(1)
        assertThat(command.context.items).contains(KeyValuePair(
            PREFERRED_PRIVATE_KEY_POLICY_KEY,
            PREFERRED_PRIVATE_KEY_POLICY_NONE
        ))
        assertRequestContext(result)
    }

    @Test
    fun `Should assign Soft HSM`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val response = HSMInfo()
        setupCompletedResponse {
            response
        }
        val result = sender.act {
            component.assignSoftHSM(
                tenantId = knownTenantId,
                category = CryptoConsts.Categories.LEDGER,
                context = mapOf(
                    NOT_FAIL_IF_ASSOCIATION_EXISTS to "YES"
                )
            )
        }
        assertSame(response, result.value)
        val command = assertOperationType<AssignSoftHSMCommand>(result)
        assertEquals (CryptoConsts.Categories.LEDGER, command.category)
        assertThat(command.context.items).contains(KeyValuePair(
            NOT_FAIL_IF_ASSOCIATION_EXISTS,
            "YES"
        ))
        assertRequestContext(result)
    }

    @Test
    fun `Should find hsm details`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val expectedValue = HSMInfo()
        setupCompletedResponse {
            expectedValue
        }
        val result = sender.act {
            component.findHSM(
                tenantId = knownTenantId,
                category = CryptoConsts.Categories.LEDGER
            )
        }
        assertNotNull(result.value)
        assertEquals(expectedValue, result.value)
        val query = assertOperationType<AssignedHSMQuery>(result)
        assertEquals(CryptoConsts.Categories.LEDGER, query.category)
        assertRequestContext(result)
    }

    @Test
    fun `Should return null for hsm details when it is not found`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        setupCompletedResponse {
            CryptoNoContentValue()
        }
        val result = sender.act {
            component.findHSM(
                tenantId = knownTenantId,
                category = CryptoConsts.Categories.LEDGER
            )
        }
        assertNull(result.value)
        val query = assertOperationType<AssignedHSMQuery>(result)
        assertEquals(CryptoConsts.Categories.LEDGER, query.category)
        assertRequestContext(result)
    }

    @Test
    fun `Should fail when response tenant id does not match the request`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        whenever(
            sender.sendRequest(any())
        ).then {
            val req = it.getArgument(0, HSMRegistrationRequest::class.java)
            val future = CompletableFuture<HSMRegistrationResponse>()
            future.complete(
                HSMRegistrationResponse(
                    CryptoResponseContext(
                        req.context.requestingComponent,
                        req.context.requestTimestamp,
                        UUID.randomUUID().toString(),
                        Instant.now(),
                        UUID.randomUUID().toString(), //req.context.tenantId
                        req.context.other
                    ), HSMInfo()
                )
            )
            future
        }
        val exception = assertThrows<CryptoServiceLibraryException> {
            component.findHSM(knownTenantId, CryptoConsts.Categories.LEDGER)
        }
        assertNotNull(exception.cause)
        assertThat(exception.cause).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `Should fail when requesting component in response does not match the request`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        whenever(
            sender.sendRequest(any())
        ).then {
            val req = it.getArgument(0, HSMRegistrationRequest::class.java)
            val future = CompletableFuture<HSMRegistrationResponse>()
            future.complete(
                HSMRegistrationResponse(
                    CryptoResponseContext(
                        UUID.randomUUID().toString(), //req.context.requestingComponent,
                        req.context.requestTimestamp,
                        UUID.randomUUID().toString(),
                        Instant.now(),
                        req.context.tenantId,
                        req.context.other
                    ), HSMInfo()
                )
            )
            future
        }
        val exception = assertThrows<CryptoServiceLibraryException> {
            component.findHSM(knownTenantId, CryptoConsts.Categories.LEDGER)
        }
        assertNotNull(exception.cause)
        assertThat(exception.cause).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `Should fail when response class is not expected`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        whenever(
            sender.sendRequest(any())
        ).then {
            val req = it.getArgument(0, HSMRegistrationRequest::class.java)
            val future = CompletableFuture<HSMRegistrationResponse>()
            future.complete(
                HSMRegistrationResponse(
                    CryptoResponseContext(
                        req.context.requestingComponent,
                        req.context.requestTimestamp,
                        UUID.randomUUID().toString(),
                        Instant.now(),
                        req.context.tenantId,
                        req.context.other
                    ), CryptoResponseContext()
                )
            )
            future
        }
        val exception = assertThrows<CryptoServiceLibraryException> {
            component.findHSM(knownTenantId, CryptoConsts.Categories.LEDGER)
        }
        assertNotNull(exception.cause)
        assertThat(exception.cause).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `Should fail when sendRequest throws CryptoServiceLibraryException exception`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val error = CryptoServiceLibraryException("Test failure.")
        whenever(sender.sendRequest(any())).thenThrow(error)
        val exception = assertThrows<CryptoServiceLibraryException> {
            component.findHSM(knownTenantId, CryptoConsts.Categories.LEDGER)
        }
        assertSame(error, exception)
    }

    @Test
    fun `Should fail when sendRequest throws an exception`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val error = RuntimeException("Test failure.")
        whenever(sender.sendRequest(any())).thenThrow(error)
        val exception = assertThrows<CryptoServiceLibraryException> {
            component.findHSM(knownTenantId, CryptoConsts.Categories.LEDGER)
        }
        assertNotNull(exception.cause)
        assertSame(error, exception.cause)
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
        Mockito.verify(sender, times(1)).close()
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
        Mockito.verify(sender, atLeast(1)).close()
    }
}
