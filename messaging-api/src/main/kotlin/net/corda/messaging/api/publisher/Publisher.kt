package net.corda.messaging.api.publisher

import net.corda.v5.base.concurrent.CordaFuture
import net.corda.messaging.api.records.Record

interface Publisher<K, V> {

    fun publish(record: Record<K, V>) : CordaFuture<Boolean>
}