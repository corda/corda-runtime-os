package net.corda.crypto.persistence.messaging

import net.corda.crypto.component.persistence.EntityKeyInfo
import java.util.concurrent.CompletableFuture

interface MessagingPersistenceProcessor<E> : AutoCloseable {
    fun publish(entity: E, vararg key: EntityKeyInfo) : List<CompletableFuture<Unit>>
    fun getValue(key: String): E?
}