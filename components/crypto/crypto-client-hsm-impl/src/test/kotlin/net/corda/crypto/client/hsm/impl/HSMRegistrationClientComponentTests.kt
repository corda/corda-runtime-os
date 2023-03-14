package net.corda.crypto.client.hsm.impl

import net.corda.crypto.cipher.suite.sha256Bytes
import net.corda.crypto.component.impl.exceptionFactories
import net.corda.crypto.component.test.utils.SendActResult
import net.corda.crypto.component.test.utils.TestConfigurationReadService
import net.corda.crypto.component.test.utils.TestRPCSender
import net.corda.crypto.component.test.utils.act
import net.corda.crypto.component.test.utils.reportDownComponents
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoConsts.HSMContext.PREFERRED_PRIVATE_KEY_POLICY_KEY
import net.corda.crypto.core.CryptoConsts.HSMContext.PREFERRED_PRIVATE_KEY_POLICY_NONE
import net.corda.data.KeyValuePair
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationRequest
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationResponse
import net.corda.data.crypto.wire.hsm.registration.commands.AssignHSMCommand
import net.corda.data.crypto.wire.hsm.registration.commands.AssignSoftHSMCommand
import net.corda.data.crypto.wire.hsm.registration.queries.AssignedHSMQuery
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.test.impl.TestLifecycleCoordinatorFactoryImpl
import net.corda.messaging.api.exception.CordaRPCAPIResponderException
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.test.util.eventually
import net.corda.v5.base.util.EncodingUtils.toHex
import net.corda.v5.crypto.exceptions.CryptoException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HSMRegistrationClientComponentTests {
    companion object {
        private val  logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        @JvmStatic
        fun knownCordaRPCAPIResponderExceptions(): List<Class<*>> =
            exceptionFactories.keys.map { Class.forName(it) }
    }

    private lateinit var knownTenantId: String
    private lateinit var sender: TestRPCSender<HSMRegistrationRequest, HSMRegistrationResponse>
    private lateinit var coordinatorFactory: TestLifecycleCoordinatorFactoryImpl
    private lateinit var configurationReadService: TestConfigurationReadService
    private lateinit var publisherFactory: PublisherFactory
    private lateinit var component: HSMRegistrationClientComponent

    @BeforeEach
    fun setup() {
        knownTenantId = toHex(UUID.randomUUID().toString().toByteArray().sha256Bytes()).take(12)
        coordinatorFactory = TestLifecycleCoordinatorFactoryImpl()
        sender = TestRPCSender(coordinatorFactory)
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
        sender.setupCompletedResponse { req ->
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
        }
    }

    private fun assertRequestContext(result: SendActResult<*>) {
        assertNotNull(sender.lastRequest)
        val context = sender.lastRequest!!.context
        assertEquals(knownTenantId, context.tenantId)
        result.assertThatIsBetween(context.requestTimestamp)
        assertEquals(HSMRegistrationClientImpl::class.simpleName, context.requestingComponent)
        assertThat(context.other.items).isEmpty()
    }

    private inline fun <reified OP> assertOperationType(): OP {
        assertNotNull(sender.lastRequest)
        assertNotNull(sender.lastRequest!!.request)
        assertThat(sender.lastRequest!!.request).isInstanceOf(OP::class.java)
        return sender.lastRequest!!.request as OP
    }

    @Test
    fun `Should assign HSM`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val response = HSMAssociationInfo()
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
        val command = assertOperationType<AssignHSMCommand>()
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
        val response = HSMAssociationInfo()
        setupCompletedResponse {
            response
        }
        val result = sender.act {
            component.assignSoftHSM(
                tenantId = knownTenantId,
                category = CryptoConsts.Categories.LEDGER
            )
        }
        assertSame(response, result.value)
        val command = assertOperationType<AssignSoftHSMCommand>()
        assertEquals (CryptoConsts.Categories.LEDGER, command.category)
        assertRequestContext(result)
    }

    @Test
    fun `Should find hsm details`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val expectedValue = HSMAssociationInfo()
        setupCompletedResponse {
            expectedValue
        }
        val result = sender.act {
            component.findHSM(
                tenantId = knownTenantId,
                category = CryptoConsts.Categories.LEDGER
            )!!
        }
        assertNotNull(result.value)
        assertEquals(expectedValue, result.value)
        val query = assertOperationType<AssignedHSMQuery>()
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
            ) == null
        }
        assertTrue(result.value)
        val query = assertOperationType<AssignedHSMQuery>()
        assertEquals(CryptoConsts.Categories.LEDGER, query.category)
        assertRequestContext(result)
    }

    @Test
    fun `Should throw IllegalStateException when response tenant id does not match the request`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        setupCompletedResponse { req ->
                HSMRegistrationResponse(
                    CryptoResponseContext(
                        req.context.requestingComponent,
                        req.context.requestTimestamp,
                        UUID.randomUUID().toString(),
                        Instant.now(),
                        UUID.randomUUID().toString(), //req.context.tenantId
                        req.context.other
                    ), HSMAssociationInfo()
                )
        }
        assertThrows(IllegalStateException::class.java) {
            component.findHSM(knownTenantId, CryptoConsts.Categories.LEDGER)
        }
    }

    @Test
    fun `Should throw IllegalStateException when requesting component in response does not match the request`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        setupCompletedResponse { req ->
                HSMRegistrationResponse(
                    CryptoResponseContext(
                        UUID.randomUUID().toString(), //req.context.requestingComponent,
                        req.context.requestTimestamp,
                        UUID.randomUUID().toString(),
                        Instant.now(),
                        req.context.tenantId,
                        req.context.other
                    ), HSMAssociationInfo()
                )
        }
        assertThrows(IllegalStateException::class.java) {
            component.findHSM(knownTenantId, CryptoConsts.Categories.LEDGER)
        }
    }

    @Test
    fun `Should throw IllegalStateException when response class is not expected`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        setupCompletedResponse { req ->
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
        }
        assertThrows(IllegalStateException::class.java) {
            component.findHSM(knownTenantId, CryptoConsts.Categories.LEDGER)
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
            component.findHSM(knownTenantId, CryptoConsts.Categories.LEDGER)
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
            component.findHSM(knownTenantId, CryptoConsts.Categories.LEDGER)
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
    fun `Should go UP and DOWN as its config reader service goes UP and DOWN`() {
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
        assertThat(sender.stopped.get()).isGreaterThanOrEqualTo(1)
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
        assertThat(sender.stopped.get()).isGreaterThanOrEqualTo(1)
    }
}

