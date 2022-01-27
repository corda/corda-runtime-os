package net.corda.cpi.upload.endpoints.v1

import net.corda.cpi.upload.endpoints.internal.CpiUploadRPCOpsInternal
import net.corda.httprpc.PluggableRPCOps
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.virtualnode.endpoints.v1.CpiUploadRPCOps
import net.corda.libs.virtualnode.endpoints.v1.HTTPCpiUploadRequestId
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import kotlin.concurrent.thread

@Component(service = [CpiUploadRPCOpsInternal::class, PluggableRPCOps::class], immediate = true)
class CpiUploadRPCOpsImpl @Activate constructor(
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) : CpiUploadRPCOpsInternal, PluggableRPCOps<CpiUploadRPCOps> {

    override val protocolVersion: Int = 1

    override val targetInterface: Class<CpiUploadRPCOps> = CpiUploadRPCOps::class.java

    // TODO: remove the following
//    init {
//        thread {
//            if (rpcSender != null) {
//                println("rpcSender is set!")
//            } else {
//                println("rpcSender is not set!")
//            }
//            Thread.sleep(1000)
//        }.start()
//    }

    companion object {
        val RPC_CONFIG = RPCConfig("", "", "", Any::class.java, Any::class.java)
    }

    @Volatile
    private var rpcSender: RPCSender<Any, Any>?  = null

    override val isRunning: Boolean
        get() = rpcSender != null

    override fun start() {
        if (!isRunning) {
            throw IllegalStateException("CpiUploadRPCOpsImpl is not yet running")
        }
    }

    override fun stop() {
        TODO("Not yet implemented")
    }

    override fun cpi(file: ByteArray): HTTPCpiUploadRequestId {
        // TODO - kyriakos - fix the endpoint to actually receive the file - needs corda rpc framework extended
        // TODO - kyriakos - split it in chunks and put it on kafka
        // TODO - kyriakos - should i then be waiting for some response from kafka here?
        // TODO - kyriakos - return HTTP response to user
        return HTTPCpiUploadRequestId(5)
    }

    override fun createAndStartRPCSender(config: SmartConfig) {
        rpcSender = publisherFactory.createRPCSender(RPC_CONFIG)
    }

    override fun setTimeout(millis: Int) {
        // no op
    }
}