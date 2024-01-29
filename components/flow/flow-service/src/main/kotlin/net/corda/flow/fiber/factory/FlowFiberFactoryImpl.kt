package net.corda.flow.fiber.factory

import co.paralleluniverse.common.util.SameThreadExecutor
import co.paralleluniverse.fibers.FiberExecutorScheduler
import net.corda.flow.fiber.FiberExceptionConstants
import net.corda.flow.fiber.FiberFuture
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowFiberImpl
import net.corda.flow.fiber.FlowLogicAndArgs
import net.corda.flow.fiber.cache.FlowFiberCache
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.metrics.CordaMetrics
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.util.UUID

@Component
@Suppress("Unused")
class FlowFiberFactoryImpl @Activate constructor(
    @Reference(service = FlowFiberCache::class)
    private val flowFiberCache: FlowFiberCache
) : FlowFiberFactory {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val currentThreadFiberExecutor =
        object : FiberExecutorScheduler("Flow Fiber scheduler", SameThreadExecutor.getExecutor()) {

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
            val flowFiber = FlowFiberImpl(id, logic, currentThreadFiberExecutor)
            return FiberFuture(flowFiber, flowFiber.startFlow(flowFiberExecutionContext))
        } catch (e: Throwable) {
            throw FlowFatalException(
                FiberExceptionConstants.UNABLE_TO_EXECUTE.format(
                    e.message ?: "No exception message provided."
                ), e
            )
        }
    }

    override fun createAndResumeFlowFiber(
        flowFiberExecutionContext: FlowFiberExecutionContext,
        suspensionOutcome: FlowContinuation
    ): FiberFuture {
        val fiber = CordaMetrics.Metric.FlowFiberDeserializationTime.builder()
            .forVirtualNode(flowFiberExecutionContext.flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(
                CordaMetrics.Tag.FlowClass,
                flowFiberExecutionContext.flowCheckpoint.flowStartContext.flowClassName
            )
            .build()
            .recordCallable {
                getFromCacheOrDeserialize(flowFiberExecutionContext)
            }!!

        return FiberFuture(
            fiber,
            fiber.resume(flowFiberExecutionContext, suspensionOutcome, currentThreadFiberExecutor)
        )
    }

    private fun getFromCacheOrDeserialize(flowFiberExecutionContext: FlowFiberExecutionContext): FlowFiber {
        val cachedFiber: FlowFiber? = try {
            flowFiberCache.get(
                flowFiberExecutionContext.flowCheckpoint.flowKey,
                flowFiberExecutionContext.flowCheckpoint.suspendCount,
                flowFiberExecutionContext.sandboxGroupContext.sandboxGroup.id
            )
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