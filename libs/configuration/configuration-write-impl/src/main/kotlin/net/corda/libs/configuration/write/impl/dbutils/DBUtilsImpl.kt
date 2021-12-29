package net.corda.libs.configuration.write.impl.dbutils

import net.corda.libs.configuration.datamodel.ConfigAuditEntity
import net.corda.libs.configuration.datamodel.ConfigEntity
import net.corda.orm.utils.transaction
import javax.persistence.EntityManagerFactory

/** An implementation of [DBUtils]. */
internal class DBUtilsImpl(
    private val entityManagerFactory: EntityManagerFactory
) : DBUtils {

    override fun writeEntities(newConfig: ConfigEntity, newConfigAudit: ConfigAuditEntity) =
        entityManagerFactory.createEntityManager().transaction { entityManager ->
            entityManager.merge(newConfig)
            entityManager.persist(newConfigAudit)
        }

    override fun readConfigEntity(section: String): ConfigEntity? {
        val entityManager = entityManagerFactory.createEntityManager()

        return try {
            entityManager.find(ConfigEntity::class.java, section)
        } finally {
            entityManager.close()
        }
    }
}