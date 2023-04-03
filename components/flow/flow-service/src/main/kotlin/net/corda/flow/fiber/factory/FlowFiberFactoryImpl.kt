package net.corda.flow.fiber.factory

import co.paralleluniverse.concurrent.util.ScheduledSingleThreadExecutor
import co.paralleluniverse.fibers.FiberExecutorScheduler
import co.paralleluniverse.fibers.FiberScheduler
import net.corda.flow.fiber.FiberFuture
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowFiberImpl
import net.corda.flow.fiber.FlowLogicAndArgs
import net.corda.flow.fiber.FiberExceptionConstants
import net.corda.flow.fiber.FlowFiberCache
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.metrics.CordaMetrics
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ExecutorService

@Component
@Suppress("Unused")
class FlowFiberFactoryImpl @Activate constructor(
    @Reference(service = FlowFiberCache::class)
    private val flowFiberCache: FlowFiberCache
) : FlowFiberFactory {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val currentScheduler: FiberScheduler = FiberExecutorScheduler(
        "Same thread scheduler",
        ScheduledSingleThreadExecutor()
    )

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
            val flowFiber = FlowFiberImpl(id, logic, currentScheduler)
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

        return FiberFuture(fiber, fiber.resume(flowFiberExecutionContext, suspensionOutcome, currentScheduler))
    }

    private fun getFromCacheOrDeserialize(flowFiberExecutionContext: FlowFiberExecutionContext): FlowFiberImpl {
        val cachedFiber: FlowFiberImpl? = try {
            flowFiberCache.get(flowFiberExecutionContext.flowCheckpoint.flowId) as FlowFiberImpl?
        } catch (e: Exception) {
            // shouldn't really be possible to get in here
            logger.warn("Failure casting flow fiber from checkpoint for flow ${flowFiberExecutionContext.flowCheckpoint.flowId}. " +
                    "Removing and skipping cache.")
            flowFiberCache.remove(flowFiberExecutionContext.flowCheckpoint.flowId)
            null
        }
        return cachedFiber ?: flowFiberExecutionContext.sandboxGroupContext.checkpointSerializer.deserialize(
            flowFiberExecutionContext.flowCheckpoint.serializedFiber.array(),
            FlowFiberImpl::class.java
        )
    }

    @Deactivate
    fun shutdown() {
        currentScheduler.shutdown()
        (currentScheduler.executor as? ExecutorService)?.shutdownNow()
    }
}