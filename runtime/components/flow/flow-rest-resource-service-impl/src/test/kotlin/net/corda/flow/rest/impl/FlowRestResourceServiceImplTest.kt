package net.corda.flow.rest.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.flow.rest.FlowRestResourceService
import net.corda.flow.rest.FlowStatusCacheService
import net.corda.flow.rest.v1.FlowRestResource
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.stream.Stream

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

class FlowRestResourceServiceImplTest {
    private val configurationReadService = mock<ConfigurationReadService>()
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService>()
    private val flowRestResource = mock<FlowRestResource>()
    private val flowStatusCacheService = mock<FlowStatusCacheService>()

    private val lifecycleTestContext = LifecycleTestContext()
    private val lifecycleCoordinator = lifecycleTestContext.lifecycleCoordinator
    private var eventHandler = mock<LifecycleEventHandler>()

    private val messagingConfig = mock<SmartConfig>()

    private lateinit var configChangeEvent: ConfigChangedEvent
    private lateinit var flowRestResourceService: FlowRestResourceService

    @BeforeEach
    fun setup() {

        flowRestResourceService = FlowRestResourceServiceImpl(
            configurationReadService,
            virtualNodeInfoReadService,
            flowRestResource,
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
        flowRestResourceService.start()
        verify(lifecycleCoordinator).start()
    }

    @Test
    fun `Test stop stops the lifecycle coordinator`() {
        flowRestResourceService.stop()
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
            assertThat(flowRestResourceService.isRunning).isTrue
        } else {
            verify(lifecycleCoordinator, never()).updateStatus(LifecycleStatus.UP)
            assertThat(flowRestResourceService.isRunning).isFalse
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
    fun `Test configuration changes initialise flow REST service`() {
        eventHandler.processEvent(configChangeEvent, lifecycleCoordinator)
        verify(flowRestResource).initialise(eq(messagingConfig), any())
    }

    @Test
    fun `Test calling the fatal error function passed to flowRestResource causes the lifecycleCoordinator to ERROR`() {
        eventHandler.processEvent(configChangeEvent, lifecycleCoordinator)
        val method = argumentCaptor<() -> Unit>()
        verify(flowRestResource).initialise(eq(messagingConfig), method.capture())

        verify(lifecycleCoordinator, times(0)).updateStatus(LifecycleStatus.ERROR)
        method.firstValue()
        verify(lifecycleCoordinator).updateStatus(LifecycleStatus.ERROR)
    }

    @Test
    fun `Test stop event closes flow status cache service`() {
        eventHandler.processEvent(StopEvent(), lifecycleCoordinator)
        verify(flowStatusCacheService).stop()
    }
}
