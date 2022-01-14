package net.corda.crypto.persistence.kafka

import java.util.concurrent.CompletableFuture

interface KafkaProxy<E> : AutoCloseable {
    fun publish(key: String, entity: E) : CompletableFuture<Unit>
    fun getValue(tenantId: String, key: String): E?
}