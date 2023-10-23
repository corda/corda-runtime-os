package com.r3.corda.demo.interop.tokens.workflows.interop

import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.interop.InteropIdentityLookup
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory


data class GetInteropIdentityArgs(val applicationName: String)

class GetInteropIdentity : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @CordaInject
    lateinit var interopIdentityLookUp: InteropIdentityLookup

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {

        try {
            val flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, GetInteropIdentityArgs::class.java)
            val interopIdentity =
                checkNotNull(interopIdentityLookUp.lookup(flowArgs.applicationName))
                { "Could not find Interop Identity for given name ${flowArgs.applicationName}" }

            return interopIdentity.x500Name + "/" + interopIdentity.groupId

        } catch (e: Exception) {
            log.warn("Failed to process GetInteropIdentity for request body " +
                    "'$requestBody' because: '${e.message}'")
            throw e
        }
    }
}
