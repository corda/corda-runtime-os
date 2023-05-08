package net.corda.flow.fiber.cache.impl

import co.paralleluniverse.concurrent.util.ScheduledSingleThreadExecutor
import co.paralleluniverse.fibers.FiberExecutorScheduler
import co.paralleluniverse.fibers.FiberScheduler
import java.util.UUID
import net.corda.data.flow.FlowKey
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.fiber.FlowFiberImpl
import net.corda.flow.fiber.FlowLogicAndArgs
import net.corda.v5.application.flows.Flow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class FlowFiberCacheImplTest {

    private val cache = FlowFiberCacheImpl()
    private val holdingIdentity = HoldingIdentity("x500", "grp1")
    private val flow = mock<Flow>()
    private val flowLogic = object : FlowLogicAndArgs {
        override val logic: Flow
            get() = flow

        override fun invoke(): String {
            return "success"
        }
    }
    private val fiberScheduler: FiberScheduler = FiberExecutorScheduler(
        "Same thread scheduler",
        ScheduledSingleThreadExecutor()
    )

    @Test
    fun `put into cache`() {
        val flow1Key = FlowKey("flow1", holdingIdentity)
        val fiber = FlowFiberImpl(UUID.randomUUID(), flowLogic, fiberScheduler)
        cache.put(flow1Key, fiber)

        assertThat(cache.get(flow1Key)).isEqualTo(fiber)
    }

}