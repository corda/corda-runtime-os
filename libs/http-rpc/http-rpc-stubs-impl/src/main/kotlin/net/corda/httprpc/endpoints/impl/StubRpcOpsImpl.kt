package net.corda.httprpc.endpoints.impl

import net.corda.httprpc.security.CURRENT_RPC_CONTEXT
import net.corda.v5.base.util.contextLogger
import net.corda.v5.httprpc.api.PluggableRPCOps
import net.corda.v5.httprpc.api.RpcOps
import net.corda.v5.httprpc.api.annotations.HttpRpcGET
import net.corda.v5.httprpc.api.annotations.HttpRpcResource
import org.osgi.service.component.annotations.Component

@HttpRpcResource(name = "Stub rpc ops", description = "Stub rpc ops endpoints", path = "stubs")
interface StubRpcOps : RpcOps {

    @HttpRpcGET("ping")
    fun ping(): String
}

@Component(service = [PluggableRPCOps::class])
class StubRpcOpsImpl : StubRpcOps, PluggableRPCOps<StubRpcOps>, RpcOps {

    private companion object {
        val log = contextLogger()
    }

    override val targetInterface: Class<StubRpcOps> = StubRpcOps::class.java

    override val protocolVersion = 1

    override fun ping(): String {
        val rpcContext = CURRENT_RPC_CONTEXT.get()
        val principal = rpcContext.principal
        return "Pinged by $principal".also { log.info(it) }
    }
}