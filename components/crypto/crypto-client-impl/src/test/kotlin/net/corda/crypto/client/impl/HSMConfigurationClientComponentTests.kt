package net.corda.crypto.client.impl

import net.corda.crypto.client.impl.infra.SendActResult
import net.corda.crypto.client.impl.infra.TestConfigurationReadService
import net.corda.crypto.client.impl.infra.act
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoTenants
import net.corda.data.KeyValuePair
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.hsm.HSMInfo
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.CryptoStringResult
import net.corda.data.crypto.wire.hsm.HSMCategoryInfo
import net.corda.data.crypto.wire.hsm.HSMCategoryInfos
import net.corda.data.crypto.wire.hsm.HSMInfos
import net.corda.data.crypto.wire.hsm.MasterKeyPolicy
import net.corda.data.crypto.wire.hsm.PrivateKeyPolicy
import net.corda.data.crypto.wire.hsm.configuration.HSMConfigurationRequest
import net.corda.data.crypto.wire.hsm.configuration.HSMConfigurationResponse
import net.corda.data.crypto.wire.hsm.configuration.commands.LinkHSMCategoriesCommand
import net.corda.data.crypto.wire.hsm.configuration.commands.PutHSMCommand
import net.corda.data.crypto.wire.hsm.configuration.queries.HSMLinkedCategoriesQuery
import net.corda.data.crypto.wire.hsm.configuration.queries.HSMQuery
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.test.util.eventually
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertArrayEquals
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
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HSMConfigurationClientComponentTests {
    private lateinit var sender: RPCSender<HSMConfigurationRequest, HSMConfigurationResponse>
    private lateinit var coordinatorFactory: LifecycleCoordinatorFactory
    private lateinit var configurationReadService: TestConfigurationReadService
    private lateinit var publisherFactory: PublisherFactory
    private lateinit var component: HSMConfigurationClientComponent

    @BeforeEach
    fun setup() {
        coordinatorFactory = LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl())
        sender = mock()
        publisherFactory = mock {
            on { createRPCSender<HSMConfigurationRequest, HSMConfigurationResponse>(any(), any()) } doReturn sender
        }
        configurationReadService = TestConfigurationReadService(
            coordinatorFactory
        ).also {
            it.start()
            eventually {
                assertTrue(it.isRunning)
            }
        }
        component = HSMConfigurationClientComponent(
            coordinatorFactory = coordinatorFactory,
            publisherFactory = publisherFactory,
            configurationReadService = configurationReadService
        )
    }

    private fun setupCompletedResponse(respFactory: (HSMConfigurationRequest) -> Any) {
        whenever(
            sender.sendRequest(any())
        ).then {
            val req = it.getArgument(0, HSMConfigurationRequest::class.java)
            val future = CompletableFuture<HSMConfigurationResponse>()
            future.complete(
                HSMConfigurationResponse(
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

    private fun assertRequestContext(result: SendActResult<HSMConfigurationRequest, *>) {
        val context = result.firstRequest.context
        assertEquals(CryptoTenants.CRYPTO, context.tenantId)
        result.assertThatIsBetween(context.requestTimestamp)
        assertEquals(HSMConfigurationClientImpl::class.simpleName, context.requestingComponent)
        assertThat(context.other.items).isEmpty()
    }

    private inline fun <reified OP> assertOperationType(result: SendActResult<HSMConfigurationRequest, *>): OP {
        Assertions.assertNotNull(result.firstRequest.request)
        assertThat(result.firstRequest.request).isInstanceOf(OP::class.java)
        return result.firstRequest.request as OP
    }

    @Test
    fun `Should put HSM`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        setupCompletedResponse {
            CryptoStringResult("id-1")
        }
        val info = HSMInfo(
            "id-1",
            Instant.now(),
            "label",
            "description",
            MasterKeyPolicy.SHARED,
            "master-key",
            1,
            5000,
            listOf(
                "scheme1",
                "scheme2"
            ),
            "serviceName",
            5
        )
        val serviceConfig = "{}".toByteArray()
        val result = sender.act {
            component.putHSM(info, serviceConfig)
        }
        val command = assertOperationType<PutHSMCommand>(result)
        assertEquals("id-1", result.value)
        assertSame(info, command.info)
        assertArrayEquals(serviceConfig, command.serviceConfig.array())
        assertRequestContext(result)
    }

    @Test
    fun `Should link categories to HSM`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        setupCompletedResponse {
            CryptoNoContentValue()
        }
        val configId = UUID.randomUUID().toString()
        val links = listOf(
            HSMCategoryInfo(CryptoConsts.Categories.LEDGER, PrivateKeyPolicy.ALIASED),
            HSMCategoryInfo(CryptoConsts.Categories.TLS, PrivateKeyPolicy.WRAPPED)
        )
        val result = sender.act {
            component.linkCategories(configId, links)
        }
        val command = assertOperationType<LinkHSMCategoriesCommand>(result)
        assertEquals(configId, command.configId)
        assertSame(links, command.links)
        assertRequestContext(result)
    }

    @Test
    fun `Should get categories linked to HSM`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        setupCompletedResponse {
            HSMCategoryInfos(
                listOf(
                    HSMCategoryInfo(CryptoConsts.Categories.LEDGER, PrivateKeyPolicy.ALIASED),
                    HSMCategoryInfo(CryptoConsts.Categories.TLS, PrivateKeyPolicy.WRAPPED)
                )
            )
        }
        val configId = UUID.randomUUID().toString()
        val result = sender.act {
            component.getLinkedCategories(configId)
        }
        val query = assertOperationType<HSMLinkedCategoriesQuery>(result)
        assertEquals(configId, query.configId)
        assertRequestContext(result)
        assertNotNull(result.value)
        assertEquals(2, result.value.size)
        assertThat(result.value).anyMatch {
            it.category == CryptoConsts.Categories.LEDGER && it.keyPolicy == PrivateKeyPolicy.ALIASED
        }
        assertThat(result.value).anyMatch {
            it.category == CryptoConsts.Categories.TLS && it.keyPolicy == PrivateKeyPolicy.WRAPPED
        }
    }

    @Test
    fun `Should do HSM lookup`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val response = HSMInfos(
            listOf(
                HSMInfo(),
                HSMInfo()
            )
        )
        setupCompletedResponse {
            response
        }
        val result = sender.act {
            component.lookup(mapOf(
                CryptoConsts.HSMFilters.SERVICE_NAME_FILTER to "whatever"
            ))
        }
        val query = assertOperationType<HSMQuery>(result)
        assertThat(query.filter.items).hasSize(1)
        assertThat(query.filter.items).contains(KeyValuePair(CryptoConsts.HSMFilters.SERVICE_NAME_FILTER, "whatever"))
        assertRequestContext(result)
        assertNotNull(result.value)
        assertSame(response.items, result.value)
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
            val req = it.getArgument(0, HSMConfigurationRequest::class.java)
            val future = CompletableFuture<HSMConfigurationResponse>()
            future.complete(
                HSMConfigurationResponse(
                    CryptoResponseContext(
                        req.context.requestingComponent,
                        req.context.requestTimestamp,
                        UUID.randomUUID().toString(),
                        Instant.now(),
                        UUID.randomUUID().toString(), //req.context.tenantId
                        req.context.other
                    ), HSMInfos()
                )
            )
            future
        }
        val exception = assertThrows<CryptoServiceLibraryException> {
            component.lookup(emptyMap())
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
            val req = it.getArgument(0, HSMConfigurationRequest::class.java)
            val future = CompletableFuture<HSMConfigurationResponse>()
            future.complete(
                HSMConfigurationResponse(
                    CryptoResponseContext(
                        UUID.randomUUID().toString(), //req.context.requestingComponent,
                        req.context.requestTimestamp,
                        UUID.randomUUID().toString(),
                        Instant.now(),
                        req.context.tenantId,
                        req.context.other
                    ), HSMInfos()
                )
            )
            future
        }
        val exception = assertThrows<CryptoServiceLibraryException> {
            component.lookup(emptyMap())
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
            val req = it.getArgument(0, HSMConfigurationRequest::class.java)
            val future = CompletableFuture<HSMConfigurationResponse>()
            future.complete(
                HSMConfigurationResponse(
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
            component.lookup(emptyMap())
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
            component.lookup(emptyMap())
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
            component.lookup(emptyMap())
        }
        assertNotNull(exception.cause)
        assertSame(error, exception.cause)
    }

    @Test
    fun `Should create active implementation only after the component is up`() {
        assertFalse(component.isRunning)
        assertInstanceOf(HSMConfigurationClientComponent.InactiveImpl::class.java, component.impl)
        assertThrows<IllegalStateException> {
            component.impl.registrar
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(HSMConfigurationClientComponent.ActiveImpl::class.java, component.impl)
        assertNotNull(component.impl.registrar)
    }

    @Test
    fun `Should cleanup created resources when component is stopped`() {
        assertFalse(component.isRunning)
        assertInstanceOf(HSMConfigurationClientComponent.InactiveImpl::class.java, component.impl)
        assertThrows<IllegalStateException> {
            component.impl.registrar
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(HSMConfigurationClientComponent.ActiveImpl::class.java, component.impl)
        assertNotNull(component.impl.registrar)
        component.stop()
        eventually {
            assertFalse(component.isRunning)
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(HSMConfigurationClientComponent.InactiveImpl::class.java, component.impl)
        Mockito.verify(sender, times(1)).close()
    }

    @Test
    fun `Should go UP and DOWN as its dependencies go UP and DOWN`() {
        assertFalse(component.isRunning)
        assertInstanceOf(HSMConfigurationClientComponent.InactiveImpl::class.java, component.impl)
        assertThrows<IllegalStateException> {
            component.impl.registrar
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(HSMConfigurationClientComponent.ActiveImpl::class.java, component.impl)
        assertNotNull(component.impl.registrar)
        configurationReadService.coordinator.updateStatus(LifecycleStatus.DOWN)
        eventually {
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(HSMConfigurationClientComponent.InactiveImpl::class.java, component.impl)
        configurationReadService.coordinator.updateStatus(LifecycleStatus.UP)
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(HSMConfigurationClientComponent.ActiveImpl::class.java, component.impl)
        assertNotNull(component.impl.registrar)
        Mockito.verify(sender, atLeast(1)).close()
    }
}
