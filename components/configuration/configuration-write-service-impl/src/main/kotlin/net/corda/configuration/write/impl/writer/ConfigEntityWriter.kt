package net.corda.configuration.write.impl.writer

import net.corda.configuration.write.WrongConfigVersionException
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.datamodel.ConfigAuditEntity
import net.corda.libs.configuration.datamodel.ConfigEntity
import net.corda.orm.utils.transaction
import java.time.Clock

/** A gateway for writing configuration entities to the cluster database. */
internal class ConfigEntityWriter(dbConnectionManager: DbConnectionManager) {
    private val entityManagerFactory = dbConnectionManager.getClusterEntityManagerFactory()

    /**
     * Creates the [ConfigEntity] and [ConfigAuditEntity] represented by [req], using [clock] to generate the current
     * time.
     *
     * Checks that the [req]'s version number is aligned with the version number of the corresponding [ConfigEntity] in
     * the cluster database if one exists, then commits the updated [ConfigEntity] and new [ConfigAuditEntity] to the
     * database in a single transaction.
     *
     * @throws `RollbackException` If the database transaction cannot be committed.
     * @throws IllegalStateException/IllegalArgumentException/TransactionRequiredException If writing the entities
     *  fails for any other reason.
     *
     *  @return The updated [ConfigEntity].
     */
    fun writeEntities(req: ConfigurationManagementRequest, clock: Clock): ConfigEntity {
        val newConfig = ConfigEntity(req.section, req.config, req.schemaVersion, clock.instant(), req.updateActor)
        val newConfigAudit = ConfigAuditEntity(newConfig)
        return entityManagerFactory.transaction { entityManager ->
            val existingConfig = entityManager.find(ConfigEntity::class.java, newConfig.section)
            val updatedConfig = existingConfig?.apply { update(newConfig) } ?: newConfig

            if (req.version != updatedConfig.version) {
                throw WrongConfigVersionException(
                    "The request specified a version of ${req.version}, but the current version in the database is " +
                            "${updatedConfig.version}. These versions must match to update the cluster configuration."
                )
            }

            entityManager.persist(newConfigAudit)
            entityManager.merge(updatedConfig)
        }
    }
}