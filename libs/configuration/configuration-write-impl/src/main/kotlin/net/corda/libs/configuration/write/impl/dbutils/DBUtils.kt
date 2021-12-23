package net.corda.libs.configuration.write.impl.dbutils

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.write.impl.entities.ConfigAuditEntity
import net.corda.libs.configuration.write.impl.entities.ConfigEntity
import javax.persistence.RollbackException

/** Encapsulates database-related functionality, so that it can be stubbed during tests. */
internal interface DBUtils {
    val managedEntities: List<Class<out Any>>
        get() = listOf(ConfigEntity::class.java, ConfigAuditEntity::class.java)

    /**
     * Connects to the cluster database using the [config], and applies the Liquibase schema migrations for each of the
     * [managedEntities].
     */
    fun migrateClusterDatabase(config: SmartConfig)

    /**
     * Creates an entity manager using the [config], then writes [newConfig] and [newConfigAudit] to the cluster
     * database in a single transaction.
     *
     * @throws RollbackException If the database transaction cannot be committed.
     * @throws IllegalStateException/IllegalArgumentException/TransactionRequiredException If writing the entities
     *  fails for any other reason.
     */
    fun writeEntities(config: SmartConfig, newConfig: ConfigEntity, newConfigAudit: ConfigAuditEntity)

    /**
     * Creates an entity manager using the [config], then reads the [ConfigEntity] for the specified [section]. Returns
     * null if no config exists for the specified section.
     *
     * @throws IllegalStateException If the entity manager cannot be created.
     */
    fun readConfigEntity(config: SmartConfig, section: String): ConfigEntity?
}