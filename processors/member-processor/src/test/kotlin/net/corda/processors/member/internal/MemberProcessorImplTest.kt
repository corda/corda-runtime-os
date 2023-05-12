package net.corda.processors.member.internal

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.processors.member.MemberProcessor
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class MemberProcessorImplTest {

    lateinit var memberProcessor: MemberProcessor

    private val bootConfig: SmartConfig = mock()

    private val coordinator: LifecycleCoordinator = mock()
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any(), any()) } doReturn coordinator
    }

    @BeforeEach
    fun setUp() {
        memberProcessor = MemberProcessorImpl(
            lifecycleCoordinatorFactory,
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
        )

        verify(lifecycleCoordinatorFactory).createCoordinator(any(), any(), any())
    }

    @Test
    fun `Starting component starts coordinator and posts config`() {
        memberProcessor.start(bootConfig)
        verify(coordinator).start()
        verify(coordinator).postEvent(eq(BootConfigEvent(bootConfig)))
    }

    @Test
    fun `Stopping component stops coordinator`() {
        memberProcessor.stop()
        verify(coordinator).stop()
    }
}
