package net.cordax.flowworker.api.publisher

import net.corda.v5.base.concurrent.CordaFuture
import net.cordax.flowworker.api.records.Record

interface Publisher<K, V> {

    fun publish(record: Record<K, V>) : CordaFuture<Boolean>
}