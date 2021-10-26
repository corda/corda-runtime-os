package net.corda.cpi.read.impl.kafka

import com.typesafe.config.Config
import net.corda.cpi.utils.CPX_KAFKA_FILE_CACHE_ROOT_DIR_CONFIG_PATH
import net.corda.data.packaging.CPISegmentRequest
import net.corda.data.packaging.CPISegmentResponse
import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.packaging.CPI
import net.corda.packaging.converters.toAvro
import net.corda.v5.base.util.contextLogger
import org.slf4j.Logger
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CPIStreamReaderImpl(private val rpcConfig: RPCConfig<CPISegmentRequest, CPISegmentResponse>,
                          private val nodeConfig: Config,
                          private val publisherFactory: PublisherFactory): Lifecycle {

    val executorService: ExecutorService = Executors.newFixedThreadPool(10)
    val rpcSender = publisherFactory.createRPCSender(rpcConfig, nodeConfig)
    val path = Paths.get(nodeConfig.getString(CPX_KAFKA_FILE_CACHE_ROOT_DIR_CONFIG_PATH))

    companion object {
        val logger: Logger = contextLogger()
        val AWAIT_TERMINATION_TIMEOUT: Long = 10
    }

    override val isRunning: Boolean
        get() = !executorService.isShutdown

    override fun start() {
        rpcSender.start()
    }

    override fun stop() {
        rpcSender.stop()
        executorService.shutdown()
        executorService.awaitTermination(AWAIT_TERMINATION_TIMEOUT, TimeUnit.SECONDS)
    }

    fun getCPIStream(cpiIdentifier: CPI.Identifier): CompletableFuture<InputStream> {
        val respFuture = CompletableFuture<InputStream>()
        executorService.execute(CPISegmentReaderExecutor(cpiIdentifier, path, rpcSender, respFuture))
        return respFuture
    }
}

class CPISegmentReaderExecutor(private val cpiIdentifier: CPI.Identifier,
                               private val parentPath: Path,
                               private val rpcSender: RPCSender<CPISegmentRequest, CPISegmentResponse>,
                               private val cpiSegmentResponseFuture: CompletableFuture<InputStream>): Runnable {

    override fun run() {
        // TODO - Use the hash as the file name, should be in CPI.Identifier somewhere
        val filename = "${cpiIdentifier.name}-${cpiIdentifier.version}"
        val path = parentPath.resolve(filename)
        if (!Files.exists(path)) {
            val tempFilename = UUID.randomUUID().toString()
            val tempPath = parentPath.resolve(tempFilename)
            Files.createDirectories(tempPath.parent)

            Files.newByteChannel(tempPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { byteChannel ->
                var start: Long = 0
                do {
                    var atEnd = false
                    try {
                        val req = CPISegmentRequest(cpiIdentifier.toAvro(), start)
                        val response = rpcSender.sendRequest(req)
                        val segmentResp = response.get(5, TimeUnit.MINUTES)
                        byteChannel.write(segmentResp.segment)
                        start += segmentResp.segment.limit()
                        atEnd = segmentResp.atEnd
                    }
                    catch(ex: ExecutionException) {
                        ex.cause?.message?.let { msg ->
                            if (msg.startsWith("No partitions")) {
                                println("CordaRPCAPISenderException : No partitions received")
                                Thread.sleep(1000)
                            }
                            else throw ex
                        }
                    }
                } while (!atEnd)
            }
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING)
        }
        val inStream = Files.newInputStream(path)
        cpiSegmentResponseFuture.complete(inStream)
    }
}

