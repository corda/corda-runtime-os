package com.r3.corda.demo.utxo.token.selection

import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.token.selection.TokenBalance
import net.corda.v5.ledger.utxo.token.selection.TokenBalanceCriteria
import net.corda.v5.ledger.utxo.token.selection.TokenSelection
import net.corda.v5.membership.NotaryInfo
import java.math.BigDecimal

@Suppress("Unused")
class TokenBalanceQueryFlow : ClientStartableFlow {

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
        val tokenBalanceQueryMsg =
            requestBody.getRequestBodyAs(marshallingService, TokenBalanceQueryMsg::class.java)

        // Assume we are using a single notary
        val notary = notaryLookup.notaryServices.single()

        val balanceQueryCriteria = genTokenBalanceQueryCriteria(tokenBalanceQueryMsg, notary)
        val tokenBalance = tokenSelection.queryBalance(balanceQueryCriteria)!!

        return tokenBalance.toResult().toJsonStr()
    }

    private fun TokenBalance.toResult() =
        TokenBalanceQueryResponseMsg(availableBalance, totalBalance)

    private fun TokenBalanceQueryResponseMsg.toJsonStr() =
        marshallingService.format(this)

    private fun genTokenBalanceQueryCriteria(
        tokenBalanceQueryMsg: TokenBalanceQueryMsg,
        notary: NotaryInfo
    ): TokenBalanceCriteria {
        val queryCriteria = TokenBalanceCriteria(
            tokenBalanceQueryMsg.tokenType,
            digestService.hash(tokenBalanceQueryMsg.issuerBankX500.toByteArray(), DigestAlgorithmName.SHA2_256),
            notary.name,
            tokenBalanceQueryMsg.currency
        )

        if (tokenBalanceQueryMsg.ownerHash != null) {
            queryCriteria.ownerHash = digestService.parseSecureHash(tokenBalanceQueryMsg.ownerHash)
        }

        if (tokenBalanceQueryMsg.regexTag != null) {
            queryCriteria.tagRegex = tokenBalanceQueryMsg.regexTag
        }

        return queryCriteria
    }

    private data class TokenBalanceQueryMsg(
        val tokenType: String,
        val issuerBankX500: String,
        val currency: String,
        val ownerHash: String?,
        val regexTag: String?
    )

    private data class TokenBalanceQueryResponseMsg(
        val availableBalance: BigDecimal,
        val totalBalance: BigDecimal
    )
}

