package net.corda.rest.endpoints.impl

import net.corda.rest.annotations.HttpRestResource
import net.corda.rest.PluggableRestResource
import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.annotations.RestQueryParameter
import net.corda.rest.security.CURRENT_REST_CONTEXT
import org.slf4j.LoggerFactory
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
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val targetInterface = HelloRestResource::class.java

    override val protocolVersion = 99

    override fun greet(addressee: String): String {
        val restContext = CURRENT_REST_CONTEXT.get()
        val principal = restContext.principal
        return "Hello, $addressee! (from $principal)".also { log.info(it) }
    }
}