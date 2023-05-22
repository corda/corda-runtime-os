package com.r3.corda.testing.interop

import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.interop.RemoteAliasLookUp
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.LoggerFactory

@InitiatingFlow(protocol = "fetch_facade_from_alias")
class FetchFacadeFromAliasIdentityFlow : ClientStartableFlow {
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private fun getArgument(args: Map<String, String>, key: String): String {
            return checkNotNull(args[key]) { "Missing argument '$key'" }
        }
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var remoteAliasLookUp: RemoteAliasLookUp

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("FetchHoldingIdentityAliasFlow.call() starting")

        val args = requestBody.getRequestBodyAsMap(jsonMarshallingService, String::class.java, String::class.java)

        val alias = getArgument(args, "alias")
        val hostNetwork = getArgument(args, "hostNetwork")

        val aliasMember = remoteAliasLookUp.lookup(alias, hostNetwork)
        log.info("Alias member info for $alias :  '$aliasMember'")
        log.info("FetchHoldingIdentityAliasFlow.call() ending")

        return aliasMember.facadeIds.toString()
    }
}