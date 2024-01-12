package net.corda.ledger.utxo.token.cache.services

import net.corda.ledger.utxo.token.cache.entities.TokenEvent
import net.corda.metrics.CordaMetrics
import java.time.Duration

class TokenSelectionMetricsImpl : TokenSelectionMetrics {
    override fun <T> recordProcessingTime(tokenEvent: TokenEvent, block: () -> T): T {
        val start = System.nanoTime()
        val result = try {
            block()
        } finally {
            CordaMetrics.Metric.TokenSelectionExecutionTime.builder()
                .withTag(CordaMetrics.Tag.TokenSelectionEvent, tokenEvent.javaClass.simpleName)
                .build().record(Duration.ofNanos(System.nanoTime() - start))
        }
        return result
    }

    override fun <T> recordDbOperationTime(dbOperation: String, block: () -> T): T {
        val start = System.nanoTime()
        val result = block()
        CordaMetrics.Metric.TokenSelectionDbExecutionTime.builder()
            .withTag(CordaMetrics.Tag.TokenSelectionDbOperation, dbOperation)
            .build().record(Duration.ofNanos(System.nanoTime() - start))
        return result
    }

    override fun <T> entityManagerCreationTime(block: () -> T): T {
        val start = System.nanoTime()
        val result = block()
        CordaMetrics.Metric.TokenSelectionEmCreationTime.builder()
            .build().record(Duration.ofNanos(System.nanoTime() - start))
        return result
    }
}
