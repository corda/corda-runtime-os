package net.corda.processors.member.internal.lifecycle

import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.processors.member.internal.BootConfigEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
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

    lateinit var memberProcessorLifecycleHandler: MemberProcessorLifecycleHandler

    @BeforeEach
    fun setUp() {
        memberProcessorLifecycleHandler = MemberProcessorLifecycleHandler(
            configurationReadService
        )
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
}
