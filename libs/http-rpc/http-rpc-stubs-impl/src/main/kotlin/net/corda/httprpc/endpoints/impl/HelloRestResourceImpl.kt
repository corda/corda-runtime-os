package net.corda.httprpc.endpoints.impl

import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.httprpc.PluggableRestResource
import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.security.CURRENT_RPC_CONTEXT
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component

@HttpRpcResource(name = "Hello RPC API", description = "The Hello RPC API is used to test interactions via the " +
        "HTTP-RPC API. It verifies that a call to HTTP-RPC can be made, and that the identity of the user making the " +
        "call can be recognized. RBAC permissions are checked and the call is successfully processed by " +
        "the HTTP-RPC worker.",
    path = "hello")
interface HelloRestResource : RestResource {

    @HttpRpcPOST(description = "This method produces a greeting phrase for the addressee.",
        responseDescription = "A greeting phrase for the addressee")
    fun greet(@HttpRpcQueryParameter(description = "An arbitrary name can be used for the greeting.") addressee: String): String
}

@Component(service = [PluggableRestResource::class])
@Suppress("unused")
class HelloRestResourceImpl : HelloRestResource, PluggableRestResource<HelloRestResource>, RestResource {

    private companion object {
        val log = contextLogger()
    }

    override val targetInterface = HelloRestResource::class.java

    override val protocolVersion = 99

    override fun greet(addressee: String): String {
        val rpcContext = CURRENT_RPC_CONTEXT.get()
        val principal = rpcContext.principal
        return "Hello, $addressee! (from $principal)".also { log.info(it) }
    }
}