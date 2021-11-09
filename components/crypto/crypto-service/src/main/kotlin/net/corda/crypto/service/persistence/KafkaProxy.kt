package net.corda.crypto.service.persistence

import java.util.concurrent.CompletableFuture

interface KafkaProxy<E: Any> : AutoCloseable {
    fun publish(key: String, entity: E) : CompletableFuture<Unit>
    fun getValue(memberId: String, key: String): E?
}