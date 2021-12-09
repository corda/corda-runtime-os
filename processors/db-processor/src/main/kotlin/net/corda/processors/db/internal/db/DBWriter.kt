package net.corda.processors.db.internal.db

import javax.persistence.RollbackException

/** Writes entities to the cluster database. */
interface DBWriter {

    /**
     * Writes all the [entities] to the database.
     *
     * Each of the [entities] must be an instance of a class annotated with `@Entity`.
     *
     * @throws RollbackException If the database transaction cannot be committed.
     * @throws IllegalStateException/IllegalArgumentException/TransactionRequiredException If writing the entities
     *  fails for any other reason.
     */
    fun writeEntity(entities: Iterable<Any>)
}