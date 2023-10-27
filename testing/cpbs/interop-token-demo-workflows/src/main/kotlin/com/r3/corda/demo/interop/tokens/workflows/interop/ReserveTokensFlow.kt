package com.r3.corda.demo.interop.tokens.workflows.interop

import com.r3.corda.demo.interop.tokens.states.TokenState
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.interop.FacadeService
import net.corda.v5.application.interop.InteropIdentityLookup
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.util.*
import net.corda.v5.application.interop.facade.FacadeId

class ReserveTokensFlow : ClientStartableFlow {
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)

        @Suspendable
        private fun getArgument(args: Map<String, String>, key: String): String {
            return checkNotNull(args[key]) { "Missing argument '$key'" }
        }
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var facadeService: FacadeService

    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @CordaInject
    lateinit var interopIdentityLookUp: InteropIdentityLookup

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("${this::class.java.simpleName}.call() starting ...")

        @Suppress("deprecation")
        val unconsumedStates = ledgerService.findUnconsumedStatesByType(TokenState::class.java)

        val args = requestBody.getRequestBodyAsMap(jsonMarshallingService, String::class.java, String::class.java)

        val facadeId = FacadeId.of(getArgument(args, "facadeId"))
        val alias = MemberX500Name.parse(getArgument(args, "alias"))
        val uuid = getArgument(args, "payload")

        log.info("Calling facade method '$facadeId' with payload '$uuid' to $alias")

        val stateId = UUID.fromString(uuid)
        val unconsumedStatesWithId = unconsumedStates.filter { it.state.contractState.linearId == stateId }

        if (unconsumedStatesWithId.size != 1) {
            throw CordaRuntimeException("Multiple or zero states with id '$stateId' found")
        }

        val stateAndRef = unconsumedStatesWithId.first()
        val inputState = stateAndRef.state.contractState
        log.info("inputState ${inputState.participants}")

        val interOPIdentity = interopIdentityLookUp.lookup(alias.organization)

        val client: TokensFacade =
            facadeService.getProxy(facadeId, TokensFacade::class.java, interOPIdentity)

        val responseObject = client.reserveTokensV1("USD", BigDecimal(100))
        val response = responseObject.toString()

        log.info("Facade responded with '$response'")
        log.info("${this::class.java.simpleName}.call() ending")

        return response
    }
}
