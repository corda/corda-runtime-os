package com.r3.corda.testing.interop

import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.interop.FacadeService
import net.corda.v5.application.interop.RemoteAliasLookUpService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
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
    lateinit var remoteAliasLookUpService: RemoteAliasLookUpService

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var facadeService: FacadeService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("FacadeInvocationFlow.call() starting")

        val alice = "CN=Alice Gold Alias, O=Alice Corp, L=LDN, C=GB";
        val cpiName = "Gold.cpi"
        val aliasMemberInfo = remoteAliasLookUpService.lookup(alice, cpiName)
        log.info("AliasMemberInfo for Alice Gold Alias : $aliasMemberInfo")

        val aliasMembers = remoteAliasLookUpService.lookup("org.corda.interop/platform/tokens/v1.0")
        log.info("AliasMemberInfo list for facadeId : $aliasMembers")

        val args = requestBody.getRequestBodyAsMap(jsonMarshallingService, String::class.java, String::class.java)

        val interopGroupId = getArgument(args, "interopGroupId")
        val facadeId = getArgument(args, "facadeId")
        val methodName = getArgument(args, "methodName")
        val alias = MemberX500Name.parse(getArgument(args,"alias"))
        val payload = getArgument(args, "payload")

        log.info("Calling facade method '$methodName@$facadeId' with payload '$payload' to $alias")

        val client : SampleTokensFacade = facadeService.getFacade(facadeId, SampleTokensFacade::class.java, alias, interopGroupId)
        val responseObject = client.getHello(payload)
        val response = responseObject.result.toString()

        log.info("Facade responded with '$response'")
        log.info("FacadeInvocationFlow.call() ending")

        return response
    }
}
