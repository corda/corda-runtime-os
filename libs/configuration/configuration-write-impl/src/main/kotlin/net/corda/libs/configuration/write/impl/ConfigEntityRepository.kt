package net.corda.libs.configuration.write.impl

import net.corda.data.config.ConfigurationManagementRequest
import net.corda.libs.configuration.datamodel.ConfigAuditEntity
import net.corda.libs.configuration.datamodel.ConfigEntity
import net.corda.libs.configuration.write.WrongVersionException
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import java.time.Clock
import javax.persistence.EntityManagerFactory

/** A gateway for interacting with configuration entities in the cluster database. */
internal class ConfigEntityRepository(private val entityManagerFactory: EntityManagerFactory) {
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

        return entityManagerFactory.createEntityManager().transaction { entityManager ->
            val existingConfig = entityManager.find(ConfigEntity::class.java, newConfig.section)
            val updatedConfig = existingConfig?.apply { update(newConfig) } ?: newConfig

            if (req.version != updatedConfig.version) {
                throw WrongVersionException(
                    "The request specified a version of ${req.version}, but the current version in the database is " +
                            "${updatedConfig.version}. These versions must match to update the cluster configuration."
                )
            }

            entityManager.persist(newConfigAudit)
            entityManager.merge(updatedConfig)
        }
    }

    /**
     * Reads the [ConfigEntity] for the specified [section]. Returns null if no config exists for the specified section.
     *
     * @throws IllegalStateException If the entity manager cannot be created.
     */
    fun readConfigEntity(section: String): ConfigEntity? =
        entityManagerFactory.createEntityManager().use { entityManager ->
            entityManager.find(ConfigEntity::class.java, section)
        }
}