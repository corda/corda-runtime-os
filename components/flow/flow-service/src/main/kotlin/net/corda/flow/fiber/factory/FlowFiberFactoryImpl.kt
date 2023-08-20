package net.corda.flow.fiber.factory

import co.paralleluniverse.concurrent.util.ScheduledSingleThreadExecutor
import co.paralleluniverse.fibers.FiberExecutorScheduler
import co.paralleluniverse.fibers.FiberScheduler
import com.google.common.util.concurrent.ThreadFactoryBuilder
import java.util.UUID
import java.util.concurrent.ExecutorService
import net.corda.flow.fiber.FiberExceptionConstants
import net.corda.flow.fiber.FiberFuture
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowFiberImpl
import net.corda.flow.fiber.FlowLogicAndArgs
import net.corda.flow.fiber.cache.FlowFiberCache
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.metrics.CordaMetrics
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Component
@Suppress("Unused")
class FlowFiberFactoryImpl @Activate constructor(
    @Reference(service = FlowFiberCache::class)
    private val flowFiberCache: FlowFiberCache
) : FlowFiberFactory {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

//    private val scheduler = FiberExecutorScheduler("Flow fiber scheduler", ThreadPoolExecutor(5, 5,
//                                                   0L, TimeUnit.MILLISECONDS,
//                                                   ThreadFactoryBuilder().setNameFormat("flow-worker").setThreadFactory(::FastThreadLocalThread).build())
//    )

    private val currentScheduler = FiberExecutorScheduler(
        "Flow fiber scheduler",
        object : ThreadPoolExecutor(
            8,
            8,
            0L,
            TimeUnit.MILLISECONDS,
            LinkedBlockingQueue(),
            ThreadFactoryBuilder().setNameFormat("flow-worker-%d").setDaemon(false).build()
        ) {


            override fun execute(command: Runnable) {
                super.execute(command)
            }
        }
    )

//    private val currentScheduler: FiberScheduler = FiberExecutorScheduler(
//        "Same thread scheduler",
//        ScheduledSingleThreadExecutor()
//    )

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

    @Deactivate
    fun shutdown() {
        currentScheduler.shutdown()
        (currentScheduler.executor as? ExecutorService)?.shutdownNow()
    }
}