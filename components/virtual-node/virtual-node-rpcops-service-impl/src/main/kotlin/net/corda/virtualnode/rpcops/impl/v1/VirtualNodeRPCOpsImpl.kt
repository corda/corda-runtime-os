package net.corda.virtualnode.rpcops.impl.v1

import net.corda.httprpc.PluggableRPCOps
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.virtualnode.endpoints.v1.VirtualNodeRPCOps
import net.corda.libs.virtualnode.endpoints.v1.types.CpiIdentifier
import net.corda.libs.virtualnode.endpoints.v1.types.HTTPCreateVirtualNodeRequest
import net.corda.libs.virtualnode.endpoints.v1.types.HTTPCreateVirtualNodeResponse
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.schema.Schemas.Config.Companion.CONFIG_MGMT_REQUEST_TOPIC
import net.corda.virtualnode.rpcops.impl.CLIENT_NAME_HTTP
import net.corda.virtualnode.rpcops.impl.GROUP_NAME
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Duration

/** An implementation of [VirtualNodeRPCOpsInternal]. */
@Suppress("Unused")
@Component(service = [VirtualNodeRPCOpsInternal::class, PluggableRPCOps::class], immediate = true)
internal class VirtualNodeRPCOpsImpl @Activate constructor(
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) : VirtualNodeRPCOpsInternal, PluggableRPCOps<VirtualNodeRPCOps> {
    private companion object {
        // The configuration used for the RPC sender.
        private val RPC_CONFIG = RPCConfig(
            GROUP_NAME,
            CLIENT_NAME_HTTP,
            // TODO - Use correct request topic.
            CONFIG_MGMT_REQUEST_TOPIC,
            // TODO - Use correct request and response objects.
            Any::class.java,
            Any::class.java
        )
    }

    override val targetInterface = VirtualNodeRPCOps::class.java
    override val protocolVersion = 1
    private var rpcSender: RPCSender<Any, Any>? = null
    private var requestTimeout: Duration? = null
    override val isRunning = rpcSender != null && requestTimeout != null

    override fun start() = Unit

    override fun stop() {
        rpcSender?.close()
        rpcSender = null
    }

    override fun createAndStartRPCSender(config: SmartConfig) {
        rpcSender?.close()
        rpcSender = publisherFactory.createRPCSender(RPC_CONFIG, config).apply { start() }
    }

    override fun setTimeout(millis: Int) {
        this.requestTimeout = Duration.ofMillis(millis.toLong())
    }

    override fun createVirtualNode(request: HTTPCreateVirtualNodeRequest): HTTPCreateVirtualNodeResponse {
        // TODO - Create virtual node based on request.
        val cpiId = CpiIdentifier("", "", "")
        return HTTPCreateVirtualNodeResponse("", cpiId, "", "", "")
    }
}