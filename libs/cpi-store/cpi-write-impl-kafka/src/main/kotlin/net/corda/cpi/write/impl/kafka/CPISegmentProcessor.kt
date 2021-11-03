package net.corda.cpi.write.impl.kafka

import net.corda.cpi.read.CPISegmentReader
import net.corda.cpi.utils.CPI_MAX_SEGMENT_SIZE
import net.corda.data.packaging.CPISegmentRequest
import net.corda.data.packaging.CPISegmentResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.packaging.converters.toCorda
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CPISegmentProcessor(rpcConfig: RPCConfig<CPISegmentRequest, CPISegmentResponse>,
                          nodeConfig: SmartConfig,
                          private val cpiSegmentReader: CPISegmentReader,
                          subscriptionFactory: SubscriptionFactory): RPCResponderProcessor<CPISegmentRequest, CPISegmentResponse>,
         Lifecycle {

    private val executorService: ExecutorService = Executors.newFixedThreadPool(10)
    private val rpcSub = subscriptionFactory.createRPCSubscription(rpcConfig, nodeConfig, this)

    companion object {
        const val AWAIT_TERMINATION_TIMEOUT: Long = 10
    }

    override val isRunning: Boolean
        get() = !executorService.isShutdown

    override fun start() {
        rpcSub.start()
    }

    override fun stop() {
        rpcSub.stop()
        executorService.shutdown()
        executorService.awaitTermination(AWAIT_TERMINATION_TIMEOUT, TimeUnit.SECONDS)
    }

    override fun onNext(request: CPISegmentRequest, respFuture: CompletableFuture<CPISegmentResponse>) {
        executorService.execute(CPISegmentRequestExecutor(cpiSegmentReader, request, respFuture))
    }
}

class CPISegmentRequestExecutor(private val cpiSegmentReader: CPISegmentReader, private val cpiSegmentRequest: CPISegmentRequest,
                                private val cpiSegmentResponseFuture: CompletableFuture<CPISegmentResponse>): Runnable {

    override fun run() {
        // TODO - how do we send exceptions back to the client
        val response = CPISegmentResponse()
        val byteBuffer = ByteBuffer.allocate(CPI_MAX_SEGMENT_SIZE)
        val avroIdentifier = cpiSegmentRequest.identifier
        val cpiIdentifier = avroIdentifier.toCorda()
        val start = cpiSegmentRequest.start
        val isEOF = cpiSegmentReader.getCPISegment(cpiIdentifier, start, byteBuffer)
        response.atEnd = isEOF
        byteBuffer.flip()
        response.segment = byteBuffer
        cpiSegmentResponseFuture.complete(response)
    }

}
