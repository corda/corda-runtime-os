package net.corda.libs.configuration.write.impl.dbutils

import net.corda.libs.configuration.write.impl.entities.ConfigAuditEntity
import net.corda.libs.configuration.write.impl.entities.ConfigEntity

/** Encapsulates database-related functionality, so that it can be stubbed during tests. */
internal interface DBUtils {
    /** Applies the Liquibase schema migrations for each of the entities. */
    fun migrateClusterDatabase()

    /**
     * Writes [newConfig] and [newConfigAudit] to the cluster database in a single transaction.
     *
     * @throws `RollbackException` If the database transaction cannot be committed.
     * @throws IllegalStateException/IllegalArgumentException/TransactionRequiredException If writing the entities
     *  fails for any other reason.
     */
    fun writeEntities(newConfig: ConfigEntity, newConfigAudit: ConfigAuditEntity)

    /**
     * Reads the [ConfigEntity] for the specified [section]. Returns null if no config exists for the specified section.
     *
     * @throws IllegalStateException If the entity manager cannot be created.
     */
    fun readConfigEntity(section: String): ConfigEntity?
}