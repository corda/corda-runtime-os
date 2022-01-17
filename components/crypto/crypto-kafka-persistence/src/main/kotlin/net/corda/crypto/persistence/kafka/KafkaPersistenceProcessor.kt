package net.corda.crypto.persistence.kafka

import net.corda.crypto.component.persistence.EntityKeyInfo
import java.util.concurrent.CompletableFuture

interface KafkaPersistenceProcessor<E> : AutoCloseable {
    fun publish(entity: E, vararg key: EntityKeyInfo) : List<CompletableFuture<Unit>>
    fun getValue(key: String): E?
}