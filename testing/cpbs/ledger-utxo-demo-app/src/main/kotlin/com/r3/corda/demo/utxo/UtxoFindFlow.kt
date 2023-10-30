package com.r3.corda.demo.utxo

import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class UtxoFindFlow : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @CordaInject
    lateinit var marshallingService: JsonMarshallingService

    @CordaInject
    lateinit var digestService: DigestService

    @OptIn(ExperimentalTime::class)
    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Utxo find batch transaction flow starting...")
        val txIds =
            requestBody.getRequestBodyAs(marshallingService, FindBatchTransactionParameters::class.java).transactionIds

        log.info("Utxo finding transaction $txIds")
        val timeTakenBatch = measureTime {
            ledgerService.findSignedTransactions(txIds.map { digestService.parseSecureHash(it) })
        }

        log.info("TIME TAKEN (batch): $timeTakenBatch")

        val timeTakenNormal = measureTime {
            txIds.forEach {
                ledgerService.findSignedTransaction(digestService.parseSecureHash(it))
            }
        }

        log.info("TIME TAKEN (normal): $timeTakenNormal")

        return "DONE"
    }
}

data class FindBatchTransactionParameters(val transactionIds: List<String>)
