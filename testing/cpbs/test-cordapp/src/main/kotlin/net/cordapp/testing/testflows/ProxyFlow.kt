package net.cordapp.testing.testflows

import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy


@InitiatingFlow(protocol = "proxy1")
class ProxyFlow : ClientStartableFlow {
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private fun getArgument(args: Map<String, String>, key: String): String {
            return checkNotNull(args[key]) { "Missing argument '$key'" }
        }
    }

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("ProxyFlow.call() starting")

        val args = requestBody.getRequestBodyAsMap(jsonMarshallingService, String::class.java, String::class.java)

        val facadeName = getArgument(args, "facadeName")
        val methodName = getArgument(args, "methodName")
        val alias = MemberX500Name.parse(getArgument(args,"alias"))
        val payload = getArgument(args, "payload")

        log.info("Calling facade method '$methodName@$facadeName' with payload '$payload' to $alias")

        val proxyInstance = Proxy.newProxyInstance(
            this::class.java.classLoader, arrayOf<Class<*>>(SampleFacade::class.java),
            DynamicInvocationHandler(flowMessaging)
        ) as SampleFacade

        val response = proxyInstance.hello(alias, facadeName, methodName, payload)
        log.info("Facade responded with '$response'")
        log.info("ProxyFlow.call() ending")

        return response
    }
}


class DynamicInvocationHandler(private val flowMessaging: FlowMessaging) : InvocationHandler {
    @Throws(Throwable::class)
    override fun invoke(proxy: Any?, method: Method, args: Array<Any?>?): Any {
        val alias = args!![0] as MemberX500Name
        val facadeName = args[1] as String
        val methodName = args[2] as String
        val payload = args[3] as String
        log.info("ProxyFlow.call() inside real proxy ${method.name} $alias $facadeName $methodName $payload")
        val response = flowMessaging.callFacade(alias, facadeName, methodName, payload)
        log.info("ProxyFlow.call() leaving real proxy ${method.name}")
        return response
    }

    companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
}

interface SampleFacade {
    fun hello(alias: MemberX500Name, facadeName: String, methodName: String, payload: String) : String
}