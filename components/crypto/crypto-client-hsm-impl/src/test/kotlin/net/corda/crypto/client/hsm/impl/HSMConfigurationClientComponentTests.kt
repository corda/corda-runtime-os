package net.corda.crypto.client.hsm.impl

import net.corda.crypto.component.impl.exceptionFactories
import net.corda.crypto.component.test.utils.SendActResult
import net.corda.crypto.component.test.utils.TestConfigurationReadService
import net.corda.crypto.component.test.utils.TestRPCSender
import net.corda.crypto.component.test.utils.act
import net.corda.crypto.component.test.utils.reportDownComponents
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoTenants
import net.corda.data.KeyValuePair
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.CryptoStringResult
import net.corda.data.crypto.wire.hsm.HSMCategoryInfo
import net.corda.data.crypto.wire.hsm.HSMCategoryInfos
import net.corda.data.crypto.wire.hsm.HSMInfo
import net.corda.data.crypto.wire.hsm.HSMInfos
import net.corda.data.crypto.wire.hsm.MasterKeyPolicy
import net.corda.data.crypto.wire.hsm.PrivateKeyPolicy
import net.corda.data.crypto.wire.hsm.configuration.HSMConfigurationRequest
import net.corda.data.crypto.wire.hsm.configuration.HSMConfigurationResponse
import net.corda.data.crypto.wire.hsm.configuration.commands.LinkHSMCategoriesCommand
import net.corda.data.crypto.wire.hsm.configuration.commands.PutHSMCommand
import net.corda.data.crypto.wire.hsm.configuration.queries.HSMLinkedCategoriesQuery
import net.corda.data.crypto.wire.hsm.configuration.queries.HSMQuery
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.test.impl.TestLifecycleCoordinatorFactoryImpl
import net.corda.messaging.api.exception.CordaRPCAPIResponderException
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.test.util.eventually
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.failures.CryptoException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HSMConfigurationClientComponentTests {
    companion object {
        private val logger = contextLogger()

        @JvmStatic
        fun knownCordaRPCAPIResponderExceptions(): List<Class<*>> =
            exceptionFactories.keys.map { Class.forName(it) }
    }

    private lateinit var sender: TestRPCSender<HSMConfigurationRequest, HSMConfigurationResponse>
    private lateinit var coordinatorFactory: TestLifecycleCoordinatorFactoryImpl
    private lateinit var configurationReadService: TestConfigurationReadService
    private lateinit var publisherFactory: PublisherFactory
    private lateinit var component: HSMConfigurationClientComponent

    @BeforeEach
    fun setup() {
        coordinatorFactory = TestLifecycleCoordinatorFactoryImpl()
        sender = TestRPCSender(coordinatorFactory)
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
        sender.setupCompletedResponse { req ->
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
        }
    }

    private fun assertRequestContext(result: SendActResult<*>) {
        assertNotNull(sender.lastRequest)
        val context = sender.lastRequest!!.context
        assertEquals(CryptoTenants.CRYPTO, context.tenantId)
        result.assertThatIsBetween(context.requestTimestamp)
        assertEquals(HSMConfigurationClientImpl::class.simpleName, context.requestingComponent)
        assertThat(context.other.items).isEmpty()
    }

    private inline fun <reified OP> assertOperationType(): OP {
        assertNotNull(sender.lastRequest)
        assertNotNull(sender.lastRequest!!.request)
        assertThat(sender.lastRequest!!.request).isInstanceOf(OP::class.java)
        return sender.lastRequest!!.request as OP
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
        val command = assertOperationType<PutHSMCommand>()
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
        val command = assertOperationType<LinkHSMCategoriesCommand>()
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
        val query = assertOperationType<HSMLinkedCategoriesQuery>()
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
        val query = assertOperationType<HSMQuery>()
        assertThat(query.filter.items).hasSize(1)
        assertThat(query.filter.items).contains(KeyValuePair(CryptoConsts.HSMFilters.SERVICE_NAME_FILTER, "whatever"))
        assertRequestContext(result)
        assertNotNull(result.value)
        assertSame(response.items, result.value)
    }

    @Test
    fun `Should throw IllegalStateException when response tenant id does not match the request`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        setupCompletedResponse { req ->
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
        }
        assertThrows(IllegalStateException::class.java) {
            component.lookup(emptyMap())
        }
    }

    @Test
    fun `Should throw IllegalStateException when requesting component in response does not match the request`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        setupCompletedResponse { req ->
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
        }
        assertThrows(IllegalStateException::class.java) {
            component.lookup(emptyMap())
        }
    }

    @Test
    fun `Should throw IllegalStateException when response class is not expected`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        setupCompletedResponse { req ->
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
        }
        assertThrows(IllegalStateException::class.java) {
            component.lookup(emptyMap())
        }
    }

    @ParameterizedTest
    @MethodSource("knownCordaRPCAPIResponderExceptions")
    @Suppress("MaxLineLength")
    fun `Should throw exception wrapped in CordaRPCAPIResponderException when sendRequest throws it as errorType`(
        expected: Class<out Throwable>
    ) {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val error = CordaRPCAPIResponderException(
            errorType = expected.name,
            message = "Test failure."
        )
        setupCompletedResponse { throw error }
        val exception = assertThrows(expected) {
            component.lookup(emptyMap())
        }
        assertEquals(error.message, exception.message)
    }

    @Test
    fun `Should throw CryptoException when sendRequest fails`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val error = CordaRPCAPIResponderException(
            errorType = RuntimeException::class.java.name,
            message = "Test failure."
        )
        setupCompletedResponse { throw error }
        val exception = assertThrows(CryptoException::class.java) {
            component.lookup(emptyMap())
        }
        assertSame(error, exception.cause)
    }

    @Test
    fun `Should create active implementation only after the component is UP`() {
        assertFalse(component.isRunning)
        assertThrows(IllegalStateException::class.java) {
            component.impl.registrar
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(
                LifecycleStatus.UP,
                component.lifecycleCoordinator.status,
                coordinatorFactory.reportDownComponents(logger)
            )
        }
        assertNotNull(component.impl.registrar)
    }

    @Test
    fun `Should cleanup created resources when component is stopped`() {
        assertFalse(component.isRunning)
        assertThrows(IllegalStateException::class.java) {
            component.impl.registrar
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertNotNull(component.impl.registrar)
        component.stop()
        eventually {
            assertFalse(component.isRunning)
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertEquals(1, sender.stopped.get())
    }

    @Test
    fun `Should go UP and DOWN as its upstream dependencies go UP and DOWN`() {
        assertFalse(component.isRunning)
        assertThrows(IllegalStateException::class.java) {
            component.impl.registrar
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertNotNull(component.impl.registrar)
        configurationReadService.lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
        eventually {
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        configurationReadService.lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertNotNull(component.impl.registrar)
        assertEquals(0, sender.stopped.get())
    }

    @Test
    fun `Should go UP and DOWN as its downstream dependencies go UP and DOWN`() {
        assertFalse(component.isRunning)
        assertThrows(IllegalStateException::class.java) {
            component.impl.registrar
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertNotNull(component.impl.registrar)
        sender.lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
        eventually {
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        sender.lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertNotNull(component.impl.registrar)
        assertEquals(0, sender.stopped.get())
    }

    @Test
    fun `Should recreate active implementation on config change`() {
        assertFalse(component.isRunning)
        assertThrows(IllegalStateException::class.java) {
            component.impl.registrar
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val originalImpl = component.impl
        assertNotNull(component.impl.registrar)
        configurationReadService.lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
        eventually {
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        configurationReadService.lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        logger.info("REISSUING ConfigChangedEvent")
        configurationReadService.reissueConfigChangedEvent(component.lifecycleCoordinator)
        eventually {
            assertNotSame(originalImpl, component.impl)
        }
        assertEquals(1, sender.stopped.get())
    }
}
