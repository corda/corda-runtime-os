package com.r3.corda.testing.interop

import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.interop.FacadeService
import net.corda.v5.application.interop.InteropIdentityLookup
import net.corda.v5.application.interop.facade.FacadeId
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.LoggerFactory

@InitiatingFlow(protocol = "invoke_facade_method")
class FacadeInvocationFlow : ClientStartableFlow {
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private fun getArgument(args: Map<String, String>, key: String): String {
            return checkNotNull(args[key]) { "Missing argument '$key'" }
        }
    }

    @CordaInject
    lateinit var interopIdentityLookUp: InteropIdentityLookup

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var facadeService: FacadeService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("FacadeInvocationFlow.call() starting")
        val args = requestBody.getRequestBodyAsMap(jsonMarshallingService, String::class.java, String::class.java)

        val facadeId = FacadeId.of(getArgument(args, "facadeId"))
        val methodName = getArgument(args, "methodName")
        val applicationName = getArgument(args,"applicationName")
        val payload = getArgument(args, "payload")

        val interopIdentityInfo = checkNotNull(interopIdentityLookUp.lookup(applicationName)) {
            "No interop identity with application name '$applicationName' was found."
        }

        log.info("InteropIdentityInfo for $applicationName: $interopIdentityInfo")

        if (!interopIdentityInfo.facadeIds.contains(facadeId)) {
            throw IllegalArgumentException("facade with facadeId : $facadeId is not supported by alias : $applicationName")
        }

        log.info("Calling facade method $methodName.$facadeId@$applicationName with payload '$payload'")

        val client: SampleTokensFacade = facadeService.getProxy(facadeId, SampleTokensFacade::class.java, interopIdentityInfo)
        val response = client.getHello(payload)

        log.info("Facade responded with '$response'")
        log.info("FacadeInvocationFlow.call() ending")

        return response
    }
}
