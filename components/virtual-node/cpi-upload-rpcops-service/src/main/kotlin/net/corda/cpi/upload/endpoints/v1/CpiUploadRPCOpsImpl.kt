package net.corda.cpi.upload.endpoints.v1

import net.corda.cpi.upload.endpoints.internal.CpiUploadRPCOpsInternal
import net.corda.httprpc.PluggableRPCOps
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.virtualnode.endpoints.v1.CpiUploadRPCOps
import net.corda.libs.virtualnode.endpoints.v1.HTTPCpiUploadRequestId
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.io.InputStream
import java.time.Duration

@Component(service = [CpiUploadRPCOpsInternal::class, PluggableRPCOps::class], immediate = true)
class CpiUploadRPCOpsImpl @Activate constructor(
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) : CpiUploadRPCOpsInternal, PluggableRPCOps<CpiUploadRPCOps> {

    override val protocolVersion: Int = 1
    private var rpcRequestTimeout: Duration? = null

    override val targetInterface: Class<CpiUploadRPCOps> = CpiUploadRPCOps::class.java

    companion object {
        val RPC_CONFIG = RPCConfig("", "", "", Any::class.java, Any::class.java)
    }

    private var rpcSender: RPCSender<Any, Any>?  = null

    override val isRunning get() = rpcSender?.isRunning ?: false && rpcRequestTimeout != null

    override fun start() = Unit

    override fun stop() {
        rpcSender?.close()
        rpcSender = null
    }

    override fun cpi(file: InputStream): HTTPCpiUploadRequestId {
        // TODO - kyriakos - fix the endpoint to actually receive the file - needs corda rpc framework extended
        // TODO - kyriakos - validation of CPI -> check it is well formed - maybe in a subsequent PR
        // TODO - kyriakos - split it in chunks and put it on kafka
        // TODO - kyriakos - should i then be waiting for some response from kafka here?
        // TODO - kyriakos - return HTTP response to user
        return HTTPCpiUploadRequestId(5)
    }

    override fun createAndStartRpcSender(config: SmartConfig) {
        rpcSender?.close()
        rpcSender = publisherFactory.createRPCSender(RPC_CONFIG, config)
        rpcSender!!.start()
    }

    override fun setRpcRequestTimeout(rpcRequestTimeout: Duration) {
        this.rpcRequestTimeout = rpcRequestTimeout
    }
}

class CpiUploadRPCOpsException(message: String?, cause: Exception? = null) : CordaRuntimeException(message, cause)