package net.corda.permissions.model.test

import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.schema.CordaDb
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.permissions.model.RbacEntities
import net.corda.permissions.model.User
import net.corda.test.util.LoggingUtils.emphasise
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.framework.FrameworkUtil
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import org.slf4j.LoggerFactory
import java.io.StringWriter
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManagerFactory

@ExtendWith(ServiceExtension::class)
class RbacEntitiesTest {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        @InjectService
        lateinit var entityManagerFactoryFactory: EntityManagerFactoryFactory
        @InjectService
        lateinit var lbm: LiquibaseSchemaMigrator

        lateinit var emf: EntityManagerFactory

        private val dbConfig: EntityManagerConfiguration = DbUtils.getEntityManagerConfiguration("rbac")

        @Suppress("unused")
        @JvmStatic
        @BeforeAll
        fun setupEntities() {

            val schemaClass = DbSchema::class.java
            val bundle = FrameworkUtil.getBundle(schemaClass)
            logger.info("RBAC schema bundle $bundle".emphasise())

            logger.info("Create Schema for ${dbConfig.dataSource.connection.metaData.url}".emphasise())
            val fullName = schemaClass.packageName + ".rbac"
            val resourcePrefix = fullName.replace('.', '/')
            val cl = ClassloaderChangeLog(
                linkedSetOf(
                    ClassloaderChangeLog.ChangeLogResourceFiles(
                        fullName,
                        listOf("$resourcePrefix/db.changelog-master.xml"),
                        classLoader = schemaClass.classLoader
                    )
                )
            )
            StringWriter().use {
                lbm.createUpdateSql(dbConfig.dataSource.connection, cl, it)
                logger.info("Schema creation SQL: $it")
            }
            lbm.updateDb(dbConfig.dataSource.connection, cl)

            logger.info("Create Entities".emphasise())

            emf = entityManagerFactoryFactory.create(CordaDb.RBAC.persistenceUnitName, RbacEntities.classes.toList(), dbConfig)
        }

        @Suppress("unused")
        @AfterAll
        @JvmStatic
        fun done() {
            if (this::emf.isInitialized) {
                emf.close()
            }
        }
    }

    @Test
    fun `test user creation`() {
        val id = UUID.randomUUID().toString()
        val user = User(
            id,
            Instant.now(),
            "fullName",
            "loginName-$id",
            true,
            "saltValue",
            "hashedPassword",
            null,
            null
        )
        emf.transaction { em -> em.persist(user) }
        emf.use { em ->
            val retrievedUser = em.createQuery("from User where id = '$id'", user.javaClass).singleResult
            Assertions.assertThat(retrievedUser).isEqualTo(user)
        }
    }
}