package com.r3.corda.demo.utxo

import java.math.BigDecimal
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.observer.UtxoTokenPoolKey
import net.corda.v5.ledger.utxo.token.selection.TokenBalance
import net.corda.v5.ledger.utxo.token.selection.TokenBalanceCriteria
import net.corda.v5.ledger.utxo.token.selection.TokenSelection
import net.corda.v5.membership.NotaryInfo
import org.slf4j.LoggerFactory


class TokenBalanceQueryFlow : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var tokenSelection: TokenSelection

    @CordaInject
    lateinit var marshallingService: JsonMarshallingService

    @CordaInject
    lateinit var digestService: DigestService

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        val tokenQueryBalanceMsg =
            requestBody.getRequestBodyAs(marshallingService, TokenQueryBalanceMsg::class.java)

        // Assume we are using a single notary
        val notary = notaryLookup.notaryServices.single()

        val balanceQueryCriteria = genTokenBalanceQueryCriteria(tokenQueryBalanceMsg, notary)
        val tokenBalance = tokenSelection.queryBalance(balanceQueryCriteria)!!

        return tokenBalance.toResult().toJsonStr()
    }

    private fun TokenBalance.toResult() =
        TokenQueryBalanceResponseMsg(balance, balanceIncludingClaimedTokens)

    private fun TokenQueryBalanceResponseMsg.toJsonStr() =
        marshallingService.format(this)

    private fun genTokenBalanceQueryCriteria(tokenQueryBalanceMsg: TokenQueryBalanceMsg, notary: NotaryInfo) =
        TokenBalanceCriteria(
            UtxoTokenPoolKey(
                tokenQueryBalanceMsg.tokenType,
                digestService.hash(tokenQueryBalanceMsg.issuerBankX500.toByteArray(), DigestAlgorithmName.SHA2_256),
                tokenQueryBalanceMsg.currency
            ),
            notary.name
        )

    private data class TokenQueryBalanceMsg(val tokenType: String, val issuerBankX500: String, val currency: String)

    private data class TokenQueryBalanceResponseMsg(
        val balance: BigDecimal,
        val balanceIncludingClaimedTokens: BigDecimal
    )
}
