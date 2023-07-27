package net.corda.flow.fiber.factory

import co.paralleluniverse.common.util.SameThreadExecutor
import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.FiberExecutorScheduler
import com.google.common.util.concurrent.ThreadFactoryBuilder
import net.corda.flow.fiber.*
import net.corda.flow.fiber.cache.FlowFiberCache
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.metrics.CordaMetrics
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.*

@Component
@Suppress("Unused")
class FlowFiberFactoryImpl @Activate constructor(
    @Reference(service = FlowFiberCache::class)
    private val flowFiberCache: FlowFiberCache
) : FlowFiberFactory {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val threadCount = 20
    private val fiberExecutorScheduler = FiberExecutorScheduler("Multi-threaded executor scheduler",
        Executors.newFixedThreadPool(threadCount))
    private val currentThreadFiberExecutor = object : FiberExecutorScheduler("Flow Fiber scheduler", SameThreadExecutor.getExecutor()) {
        override fun isCurrentThreadInScheduler(): Boolean {
            return true
        }
    }

    override fun createAndStartFlowFiber(
        flowFiberExecutionContext: FlowFiberExecutionContext,
        flowId: String,
        logic: FlowLogicAndArgs
    ): FiberFuture {
        val id = try {
            UUID.fromString(flowId)
        } catch (e: IllegalArgumentException) {
            throw FlowFatalException(FiberExceptionConstants.INVALID_FLOW_KEY.format(flowId), e)
        }
        try {
//            val flowFiber = FlowFiberImpl(id, logic, fiberExecutorScheduler)
            val flowFiber = FlowFiberImpl(id, logic, currentThreadFiberExecutor)
            return FiberFuture(flowFiber, flowFiber.startFlow(flowFiberExecutionContext))
        } catch (e: Throwable) {
            throw FlowFatalException(FiberExceptionConstants.UNABLE_TO_EXECUTE.format(e.message ?: "No exception message provided."), e)
        }
    }

    override fun createAndResumeFlowFiber(
        flowFiberExecutionContext: FlowFiberExecutionContext,
        suspensionOutcome: FlowContinuation
    ): FiberFuture {
        val fiber = CordaMetrics.Metric.FlowFiberDeserializationTime.builder()
            .forVirtualNode(flowFiberExecutionContext.flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowFiberExecutionContext.flowCheckpoint.flowStartContext.flowClassName)
            .build()
            .recordCallable {
                getFromCacheOrDeserialize(flowFiberExecutionContext)
            }!!

//        return FiberFuture(fiber, fiber.resume(flowFiberExecutionContext, suspensionOutcome, fiberExecutorScheduler))
        return FiberFuture(fiber, fiber.resume(flowFiberExecutionContext, suspensionOutcome, currentThreadFiberExecutor))
    }

    private fun getFromCacheOrDeserialize(flowFiberExecutionContext: FlowFiberExecutionContext): FlowFiberImpl {
        val cachedFiber: FlowFiberImpl? = try {
            flowFiberCache.get(flowFiberExecutionContext.flowCheckpoint.flowKey)
        } catch (e: Exception) {
            logger.warn("Exception when getting from flow fiber cache.", e)
            null
        }
        return cachedFiber ?: flowFiberExecutionContext.sandboxGroupContext.checkpointSerializer.deserialize(
            flowFiberExecutionContext.flowCheckpoint.serializedFiber.array(),
            FlowFiberImpl::class.java
        )
    }
}