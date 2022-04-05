package net.corda.reconciliation

import java.time.Duration

/**
 * Creates [Reconciler]s out of db [ReconcilerReader], Kafka [ReconcilerReader] and Kafka [ReconcilerWriter].
 */
interface ReconcilerFactory {
    @Suppress("LongParameterList")
    fun <K : Any, V : Any> create(
        dbReader: ReconcilerReader<K, V>,
        kafkaReader: ReconcilerReader<K, V>,
        writer: ReconcilerWriter<V>,
        keyClass: Class<K>,
        valueClass: Class<V>,
        reconciliationInterval: Duration
    ): Reconciler
}