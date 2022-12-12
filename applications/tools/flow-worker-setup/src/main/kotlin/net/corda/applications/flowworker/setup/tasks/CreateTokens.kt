package net.corda.applications.flowworker.setup.tasks

import net.corda.applications.flowworker.setup.Task
import net.corda.applications.flowworker.setup.TaskContext
import net.corda.data.ledger.utxo.token.selection.data.Token
import net.corda.data.ledger.utxo.token.selection.data.TokenAmount
import net.corda.data.ledger.utxo.token.selection.data.TokenLedgerChange
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.util.*
import kotlin.random.Random

class CreateTokens(private val context: TaskContext) : Task {

    private val currency = listOf("USD", "GBP", "EUR", "CHF", "DKK")
    private val buckets = listOf("B1", "B2", "B3", "B4")

    override fun execute() {
        // Create 1000 Tokens in total
        val records = currency.map { create200Tokens(context.startArgs.shortHolderId, it) }
        context.log.info("Publishing ${records.size} ledger events  each with 200 Tokens")
        context.publish(records)
    }

    private fun create200Tokens(shortHolderId: String, ccy: String): Record<TokenPoolCacheKey, TokenPoolCacheEvent> {
        val tokenSetKey = TokenPoolCacheKey().apply {
            this.shortHolderId = shortHolderId
            this.tokenType = "coin"
            this.issuerHash = shortHolderId.toSecureHashString()
            this.notaryX500Name = "CN=Alice, O=Alice Corp, L=LDN, C=GB"
            this.symbol = ccy
        }

        val tokens = if (ccy == "GBP") {
            List(10) { it }.map {
                Token().apply {
                    this.stateRef = UUID.randomUUID().toStateRef().toString()
                    this.ownerHash = shortHolderId.toSecureHashString()
                    this.tag = "B1"
                    this.amount = 10L.toTokenAmount()
                }
            }
        } else {
            buckets.flatMap { bucket ->
                List(50) { Random.nextLong(1, 50) }.map {
                    Token().apply {
                        this.stateRef = UUID.randomUUID().toStateRef().toString()
                        this.ownerHash = shortHolderId.toSecureHashString()
                        this.tag = bucket
                        this.amount = it.toTokenAmount()
                    }
                }
            }
        }

        val ledgerEvent = TokenLedgerChange().apply {
            this.poolKey = tokenSetKey
            this.producedTokens = tokens
            this.consumedTokens = listOf()
        }

        val tokenEvent = TokenPoolCacheEvent().apply {
            this.poolKey = tokenSetKey
            this.payload = ledgerEvent
        }

        return Record(Schemas.Services.TOKEN_CACHE_EVENT, tokenSetKey, tokenEvent)
    }

    private fun Long.toTokenAmount(): TokenAmount {
        val decimal = BigDecimal(this)
        return  TokenAmount().apply {
            unscaledValue = ByteBuffer.wrap(decimal.unscaledValue().toByteArray())
            scale = decimal.scale()
        }
    }
}