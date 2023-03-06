package net.corda.membership.persistence.client

import net.corda.messaging.api.records.Record

/**
 * A persistent operation. Can be executed to send to the command to the persistence layer or via RPC
 * create a collection of commands to post to the message bus to be handled eventually.
 */
interface MembershipPersistenceOperation<T> {

    /**
     * Execute the command now and return a `MembershipPersistenceResult` to represent the result. */
    fun execute(): MembershipPersistenceResult<T>

    /**
     * Similar to `execute` but throw an exception in case of an error. */
    fun getOrThrow(): T = execute().getOrThrow()

    /**
     * Create a collection of commands that can be posted to the message bus and which the persistence layer
     * will handle eventually.
     */
    fun createAsyncCommands(): Collection<Record<*, *>>
}
