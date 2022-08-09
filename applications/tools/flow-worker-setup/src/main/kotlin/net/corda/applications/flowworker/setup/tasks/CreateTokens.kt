package net.corda.applications.flowworker.setup.tasks

import net.corda.applications.flowworker.setup.Task
import net.corda.applications.flowworker.setup.TaskContext
import net.corda.data.services.Token
import net.corda.data.services.TokenEvent
import net.corda.data.services.TokenLedgerEvent
import net.corda.data.services.TokenSetKey
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.random.Random

class CreateTokens(private val context: TaskContext) : Task {

    private val currency = listOf("USD", "GBP", "EUR", "CHF", "DKK")
    private val buckets = listOf("B1", "B2", "B3", "B4")

    private companion object {
        val log : Logger = LoggerFactory.getLogger("SetupVirtualNode")
    }

    override fun execute() {
        // Create 1000 Tokens in total
        val records = currency.map { create200Tokens(context.startArgs.shortHolderId, it) }
        log.info("Publishing ${records.size} ledger events  each with 200 Tokens")
        context.publish(records)
    }

    private fun create200Tokens(shortHolderId: String, ccy: String) : Record<TokenSetKey, TokenEvent> {
        val tokenSetKey = TokenSetKey().apply {
            this.shortHolderId = shortHolderId
            this.tokenType = "coin"
            this.issuerHash = shortHolderId
            this.notaryHash = "n1"
            this.symbol = ccy
        }

        val tokens = buckets.flatMap { bucket ->
            List(50) { Random.nextLong(1, 50) }.map {
                Token().apply {
                    this.stateRef = UUID.randomUUID().toString()
                    this.ownerHash = shortHolderId
                    this.tag = bucket
                    this.amount = it
                }
            }
        }

        val ledgerEvent = TokenLedgerEvent().apply {
            this.tokenSetKey = tokenSetKey
            this.producedTokens = tokens
            this.consumedTokens = listOf()
        }

        val tokenEvent = TokenEvent().apply {
            this.tokenSetKey = tokenSetKey
            this.payload = ledgerEvent
        }

        return Record(Schemas.Services.TOKEN_CACHE_EVENT, tokenSetKey, tokenEvent)
    }
}