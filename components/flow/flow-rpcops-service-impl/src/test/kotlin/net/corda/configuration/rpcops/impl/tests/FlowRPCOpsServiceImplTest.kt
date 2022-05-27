package net.corda.configuration.rpcops.impl.tests

import java.util.stream.Stream
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.flow.rpcops.FlowRPCOpsService
import net.corda.flow.rpcops.FlowStatusCacheService
import net.corda.flow.rpcops.impl.CacheLoadCompleteEvent
import net.corda.flow.rpcops.impl.FlowRPCOpsServiceImpl
import net.corda.flow.rpcops.v1.FlowRpcOps
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LifecycleTestContext {
    val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>()
    val lifecycleCoordinator = mock<LifecycleCoordinator>()
    val lifecycleEventRegistration = mock<RegistrationHandle>()

    private var eventHandler: LifecycleEventHandler? = null

    init {
        whenever(lifecycleCoordinatorFactory.createCoordinator(any(), any())).thenReturn(lifecycleCoordinator)
        whenever(lifecycleCoordinator.followStatusChangesByName(any())).thenReturn(lifecycleEventRegistration)
    }

    fun getEventHandler(): LifecycleEventHandler {
        if (eventHandler == null) {
            argumentCaptor<LifecycleEventHandler>().apply {
                verify(lifecycleCoordinatorFactory).createCoordinator(
                    any(),
                    capture()
                )
                eventHandler = firstValue
            }
        }

        return eventHandler!!
    }
}

class FlowRPCOpsServiceImplTest {
    private val configurationReadService = mock<ConfigurationReadService>()
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService>()
    private val flowRpcOps = mock<FlowRpcOps>()
    private val flowStatusCacheService = mock<FlowStatusCacheService>()

    private val lifecycleTestContext = LifecycleTestContext()
    private val lifecycleCoordinator = lifecycleTestContext.lifecycleCoordinator
    private var eventHandler = mock<LifecycleEventHandler>()

    private val messagingConfig = mock<SmartConfig>()

    private lateinit var configChangeEvent: ConfigChangedEvent
    private lateinit var flowRPCOpsService: FlowRPCOpsService

    @BeforeEach
    fun setup() {

        flowRPCOpsService = FlowRPCOpsServiceImpl(
            configurationReadService,
            virtualNodeInfoReadService,
            flowRpcOps,
            flowStatusCacheService,
            lifecycleTestContext.lifecycleCoordinatorFactory
        )

        eventHandler = lifecycleTestContext.getEventHandler()

        val bootConfig: SmartConfig = mock()

        val configs = mapOf(
            BOOT_CONFIG to bootConfig,
            MESSAGING_CONFIG to messagingConfig
        )

        configChangeEvent = ConfigChangedEvent(configs.keys.toSet(), configs)
    }

    companion object {
        @JvmStatic
        fun eventsToSignalUp(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(listOf(StartEvent()), false),
                Arguments.of(listOf(CacheLoadCompleteEvent()), false),
                Arguments.of(listOf(StartEvent(), CacheLoadCompleteEvent()), true),
                Arguments.of(listOf(CacheLoadCompleteEvent(), StartEvent()), true),
            )
        }
    }

    @Test
    fun `Test start starts the lifecycle coordinator`() {
        flowRPCOpsService.start()
        verify(lifecycleCoordinator).start()
    }

    @Test
    fun `Test stop stops the lifecycle coordinator`() {
        flowRPCOpsService.stop()
        verify(lifecycleCoordinator).stop()
    }

    @ParameterizedTest(name = "Test UP is signaled when start and cache loaded events are received in any order")
    @MethodSource("eventsToSignalUp")
    fun `Test UP is signaled when start and cache loaded events are received in any order`(
        events: List<LifecycleEvent>,
        upSignaled: Boolean
    ) {
        events.forEach { eventHandler.processEvent(it, lifecycleCoordinator) }

        if (upSignaled) {
            verify(lifecycleCoordinator).updateStatus(LifecycleStatus.UP)
            assertThat(flowRPCOpsService.isRunning).isTrue
        } else {
            verify(lifecycleCoordinator, never()).updateStatus(LifecycleStatus.UP)
            assertThat(flowRPCOpsService.isRunning).isFalse
        }
    }

    @Test
    fun `Test start event registers and starts all dependent components`() {
        eventHandler.processEvent(StartEvent(), lifecycleCoordinator)

        verify(lifecycleCoordinator).followStatusChangesByName(
            eq(
                setOf(
                    LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                    LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
                    LifecycleCoordinatorName.forComponent<FlowStatusCacheService>(),
                )
            )
        )

        verify(configurationReadService).start()
        verify(virtualNodeInfoReadService).start()
        verify(flowStatusCacheService).start()
    }

    @Test
    fun `Test configuration update subscription on registration status change`() {
        eventHandler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), lifecycleCoordinator)

        verify(configurationReadService).registerComponentForUpdates(
            eq(lifecycleCoordinator),
            eq(setOf(BOOT_CONFIG, MESSAGING_CONFIG))
        )
    }

    @Test
    fun `Test configuration changes initialise flow status cache service`() {
        eventHandler.processEvent(configChangeEvent, lifecycleCoordinator)
        verify(flowStatusCacheService).initialise(eq(messagingConfig))
    }

    @Test
    fun `Test configuration changes initialise flow RPC service`() {
        eventHandler.processEvent(configChangeEvent, lifecycleCoordinator)
        verify(flowRpcOps).initialise(eq(messagingConfig))
    }

    @Test
    fun `Test stop event closes flow status cache service`() {
        eventHandler.processEvent(StopEvent(), lifecycleCoordinator)
        verify(flowStatusCacheService).close()
    }
}