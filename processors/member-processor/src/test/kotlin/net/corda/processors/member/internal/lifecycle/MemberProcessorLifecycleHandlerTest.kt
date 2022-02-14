package net.corda.processors.member.internal.lifecycle

import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.processors.member.internal.BootConfigEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class MemberProcessorLifecycleHandlerTest {

    private val configurationReadService: ConfigurationReadService = mock()
    private val registrationHandle = mock<RegistrationHandle>()
    private val coordinator: LifecycleCoordinator = mock {
        on { followStatusChangesByName(any()) } doReturn registrationHandle
    }

    interface Component1 : Lifecycle
    interface Component2 : Lifecycle

    val component1 = mock<Component1>()
    val component2 = mock<Component2>()
    val components = listOf(component1, component2)

    private val dependentComponents: DependentComponents = DependentComponents.of(
        ::component1,
        ::component2,
    )

    lateinit var memberProcessorLifecycleHandler: MemberProcessorLifecycleHandler

    @BeforeEach
    fun setUp() {
        memberProcessorLifecycleHandler = MemberProcessorLifecycleHandler(
            configurationReadService,
            dependentComponents
        )
    }

    @Test
    fun `start event`() {
        memberProcessorLifecycleHandler.processEvent(StartEvent(), coordinator)

        verify(coordinator).followStatusChangesByName(
            eq(
                setOf(
                    LifecycleCoordinatorName.forComponent<Component1>(),
                    LifecycleCoordinatorName.forComponent<Component2>(),
                )
            )
        )
        verify(registrationHandle, never()).close()
        for (component in components) {
            verify(component).start()
        }
    }

    @Test
    fun `start event called a second time closes previously created registration handle`() {
        memberProcessorLifecycleHandler.processEvent(StartEvent(), coordinator)
        memberProcessorLifecycleHandler.processEvent(StartEvent(), coordinator)

        verify(coordinator, times(2)).followStatusChangesByName(
            eq(
                setOf(
                    LifecycleCoordinatorName.forComponent<Component1>(),
                    LifecycleCoordinatorName.forComponent<Component2>(),
                )
            )
        )
        verify(registrationHandle).close()
        for (component in components) {
            verify(component, times(2)).start()
        }
    }

    @Test
    fun `Processor coordinator status changes based on registration status change event`() {

        fun testStatus(status: LifecycleStatus) {
            memberProcessorLifecycleHandler.processEvent(
                RegistrationStatusChangeEvent(registrationHandle, status),
                coordinator
            )
            verify(coordinator).updateStatus(eq(status), any())
        }

        testStatus(LifecycleStatus.UP)
        testStatus(LifecycleStatus.DOWN)
        testStatus(LifecycleStatus.ERROR)
    }

    @Test
    fun `boot config event`() {
        val config: SmartConfig = mock()
        memberProcessorLifecycleHandler.processEvent(BootConfigEvent(config), coordinator)
        verify(configurationReadService).bootstrapConfig(eq(config))
    }

    @Test
    fun `stop event stops dependency services`() {
        memberProcessorLifecycleHandler.processEvent(StopEvent(), coordinator)
        for (component in components) {
            verify(component).stop()
        }
    }

    @Test
    fun `stop event after start event closes registration handle`() {
        memberProcessorLifecycleHandler.processEvent(StartEvent(), coordinator)
        memberProcessorLifecycleHandler.processEvent(StopEvent(), coordinator)
        verify(registrationHandle).close()
        for (component in components) {
            verify(component).stop()
        }
    }
}