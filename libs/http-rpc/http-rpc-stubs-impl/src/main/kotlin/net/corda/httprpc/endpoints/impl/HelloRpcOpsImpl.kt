package net.corda.httprpc.endpoints.impl

import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.security.CURRENT_RPC_CONTEXT
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component

@HttpRpcResource(name = "Hello RPC API", description = "Endpoint to test interaction via HTTP RPC API. " +
        "It verifies that a call to HTTP RPC can be made, identity of the user making a call is recognized, " +
        "RBAC permissions checked and the call is successfully processed by the HTTP RPC worker.",
    path = "hello")
interface HelloRpcOps : RpcOps {

    @HttpRpcPOST(description = "Produces a greeting phrase for the addressee.")
    fun greet(@HttpRpcQueryParameter(description = "Can be an arbitrary name to be greeted.") addressee: String): String
}

@Component(service = [PluggableRPCOps::class])
@Suppress("unused")
class HelloRpcOpsImpl : HelloRpcOps, PluggableRPCOps<HelloRpcOps>, RpcOps {

    private companion object {
        val log = contextLogger()
    }

    override val targetInterface = HelloRpcOps::class.java

    override val protocolVersion = 99

    override fun greet(addressee: String): String {
        val rpcContext = CURRENT_RPC_CONTEXT.get()
        val principal = rpcContext.principal
        return "Hello, $addressee! (from $principal)".also { log.info(it) }
    }
}