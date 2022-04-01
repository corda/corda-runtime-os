package net.corda.crypto.persistence.messaging.impl

import java.util.concurrent.CompletableFuture

interface MessagingPersistenceProcessor<E> : AutoCloseable {
    fun publish(entity: E, vararg key: EntityKeyInfo) : List<CompletableFuture<Unit>>
    fun getValue(key: String): E?
}