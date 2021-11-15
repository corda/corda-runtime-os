package net.corda.permissions.model.test

import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.LiquibaseSchemaMigrator.Companion.PUBLIC_SCHEMA
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.permissions.model.ChangeAudit
import net.corda.permissions.model.Group
import net.corda.permissions.model.GroupProperty
import net.corda.permissions.model.Permission
import net.corda.permissions.model.Role
import net.corda.permissions.model.RoleGroupAssociation
import net.corda.permissions.model.RolePermissionAssociation
import net.corda.permissions.model.RoleUserAssociation
import net.corda.permissions.model.User
import net.corda.permissions.model.UserProperty
import net.corda.test.util.LoggingUtils.emphasise
import net.corda.v5.base.util.contextLogger
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.framework.FrameworkUtil
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.io.StringWriter
import java.time.Instant
import java.util.*
import javax.persistence.EntityManagerFactory

@ExtendWith(ServiceExtension::class)
class RpcRbacEntitiesTest {
    companion object {
        private val logger = contextLogger()

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
                // Cannot use DbSchema.RPC_RBAC schema for LB here as this schema needs to be created ahead of change
                // set being applied
                lbm.createUpdateSql(dbConfig.dataSource.connection, cl, it, PUBLIC_SCHEMA)
                logger.info("Schema creation SQL: $it")
            }
            lbm.updateDb(dbConfig.dataSource.connection, cl, PUBLIC_SCHEMA)

            logger.info("Create Entities".emphasise())

            emf = entityManagerFactoryFactory.create(
                "RPC RBAC",
                listOf(
                    User::class.java,
                    Group::class.java,
                    Role::class.java,
                    Permission::class.java,
                    UserProperty::class.java,
                    GroupProperty::class.java,
                    ChangeAudit::class.java,
                    RoleUserAssociation::class.java,
                    RoleGroupAssociation::class.java,
                    RolePermissionAssociation::class.java
                ),
                dbConfig
            )
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
        val em = emf.createEntityManager()
        try {
            em.transaction.begin()
            val id = UUID.randomUUID().toString()
            val user = User(
                id, Instant.now(), "fullName", "loginName-$id", true,
                "saltValue", "hashedPassword", null, null)
            em.persist(user)
            em.transaction.commit()

            val retrievedUser = em.createQuery("from User where id = '$id'", user.javaClass).singleResult
            Assertions.assertThat(retrievedUser).isEqualTo(user)
        } finally {
            em.close()
        }
    }
}