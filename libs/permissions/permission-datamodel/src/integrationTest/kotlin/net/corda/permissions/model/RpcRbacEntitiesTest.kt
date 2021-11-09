package net.corda.permissions.model

import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.schema.Schema
import net.corda.db.testkit.DbUtils.getEntityManagerConfiguration
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.test.util.LoggingUtils.emphasise
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.junit5.service.ServiceExtension
import java.io.StringWriter
import javax.persistence.EntityManagerFactory
import net.corda.v5.base.util.contextLogger
import org.assertj.core.api.Assertions.assertThat
import org.osgi.framework.FrameworkUtil
import org.osgi.test.common.annotation.InjectService
import java.time.Instant


@ExtendWith(ServiceExtension::class)
class RpcRbacEntitiesTest {
    companion object {
        private val logger = contextLogger()

        @InjectService
        lateinit var entityManagerFactoryFactory: EntityManagerFactoryFactory
        @InjectService
        lateinit var lbm: LiquibaseSchemaMigrator

        lateinit var emf: EntityManagerFactory

        private val dbConfig: EntityManagerConfiguration = getEntityManagerConfiguration("rbac")

        @Suppress("unused")
        @JvmStatic
        @BeforeAll
        fun setupEntities() {

            val schemaClass = Schema::class.java
            val bundle = FrameworkUtil.getBundle(schemaClass)
            logger.info("RBAC schema bundle $bundle".emphasise())

            logger.info("Create Schema for ${dbConfig.dataSource.connection.metaData.url}".emphasise())
            val cl = ClassloaderChangeLog(
                linkedSetOf(
                    ClassloaderChangeLog.ChangeLogResourceFiles(
                        schemaClass.packageName + ".rbac", listOf("migration/db.changelog-master.xml"), classLoader = schemaClass.classLoader)
            ))
            StringWriter().use {
                lbm.createUpdateSql(dbConfig.dataSource.connection, cl, it)
                logger.info("Schema creation SQL: $it")
            }
            lbm.updateDb(dbConfig.dataSource.connection, cl)

            logger.info("Create Entities".emphasise())

            emf = entityManagerFactoryFactory.create(
                "RPC RBAC",
                listOf(
                    User::class.java, Group::class.java, Role::class.java,
                    Permission::class.java, UserProperty::class.java, GroupProperty::class.java, ChangeAudit::class.java
                ),
                dbConfig
            )
        }

        @Suppress("unused")
        @AfterAll
        @JvmStatic
        fun done() {
            emf.close()
        }
    }

    @Test
    fun `test user creation`() {
        val em = emf.createEntityManager()
        try {
            em.transaction.begin()
            val user = User(
                "userId", Instant.now(), "fullName", "loginName", true,
                "saltValue", "hashedPassword", null, null
            )
            em.persist(user)
            em.transaction.commit()

            assertThat(
                em.createQuery("from User", User::class.java).resultList
            ).contains(user)
        } finally {
            em.close()
        }
    }
}