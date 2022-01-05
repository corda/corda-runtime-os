package net.corda.libs.configuration.write.impl

import net.corda.libs.configuration.datamodel.ConfigAuditEntity
import net.corda.libs.configuration.datamodel.ConfigEntity
import net.corda.orm.utils.transaction
import javax.persistence.EntityManagerFactory

/** A gateway for interacting with configuration entities in the cluster database. */
internal class ConfigEntityRepository(private val entityManagerFactory: EntityManagerFactory) {
    /**
     * Writes [newConfig] and [newConfigAudit] to the cluster database in a single transaction.
     *
     * @throws `RollbackException` If the database transaction cannot be committed.
     * @throws IllegalStateException/IllegalArgumentException/TransactionRequiredException If writing the entities
     *  fails for any other reason.
     */
    fun writeEntities(newConfig: ConfigEntity, newConfigAudit: ConfigAuditEntity) =
        entityManagerFactory.createEntityManager().transaction { entityManager ->
            entityManager.merge(newConfig)
            entityManager.persist(newConfigAudit)
        }

    /**
     * Reads the [ConfigEntity] for the specified [section]. Returns null if no config exists for the specified section.
     *
     * @throws IllegalStateException If the entity manager cannot be created.
     */
    fun readConfigEntity(section: String): ConfigEntity? {
        val entityManager = entityManagerFactory.createEntityManager()

        return try {
            entityManager.find(ConfigEntity::class.java, section)
        } finally {
            entityManager.close()
        }
    }
}