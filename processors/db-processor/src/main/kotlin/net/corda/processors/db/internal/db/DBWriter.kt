package net.corda.processors.db.internal.db

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import javax.persistence.RollbackException

/** Writes entities to the cluster database. */
interface DBWriter : Lifecycle {

    /**
     * Bootstraps the [DBWriter] by providing the required information to connect to the cluster database and manage
     * entities.
     */
    fun bootstrapConfig(config: SmartConfig, managedEntities: Iterable<Class<*>>)

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