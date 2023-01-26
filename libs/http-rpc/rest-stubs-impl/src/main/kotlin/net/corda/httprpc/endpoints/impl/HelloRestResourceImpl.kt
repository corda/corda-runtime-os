package net.corda.httprpc.endpoints.impl

import net.corda.httprpc.annotations.HttpRestResource
import net.corda.httprpc.PluggableRestResource
import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpPOST
import net.corda.httprpc.annotations.RestQueryParameter
import net.corda.httprpc.security.CURRENT_REST_CONTEXT
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component

@HttpRestResource(name = "Hello Rest API", description = "The Hello Rest API is used to test interactions via the " +
        "Rest API. It verifies that a call to Rest can be made, and that the identity of the user making the " +
        "call can be recognized. RBAC permissions are checked and the call is successfully processed by " +
        "the HTTP-Rest worker.",
    path = "hello")
interface HelloRestResource : RestResource {

    @HttpPOST(description = "This method produces a greeting phrase for the addressee.",
        responseDescription = "A greeting phrase for the addressee")
    fun greet(@RestQueryParameter(description = "An arbitrary name can be used for the greeting.") addressee: String): String
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
        val rpcContext = CURRENT_REST_CONTEXT.get()
        val principal = rpcContext.principal
        return "Hello, $addressee! (from $principal)".also { log.info(it) }
    }
}