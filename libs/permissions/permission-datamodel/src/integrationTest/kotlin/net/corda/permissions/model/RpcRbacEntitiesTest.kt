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

        private val permDataModelBundle = run {
            val bundle = FrameworkUtil.getBundle(this::class.java).bundleContext.bundles.single { bundle ->
                bundle.symbolicName == "net.corda.permission-datamodel"
            }
            logger.info("PermDataModelBundle bundle $bundle".emphasise())
            bundle
        }

        private val userClass = permDataModelBundle.loadClass("net.corda.permissions.model.User")
        private val groupClass = permDataModelBundle.loadClass("net.corda.permissions.model.Group")
        private val roleClass = permDataModelBundle.loadClass("net.corda.permissions.model.Role")
        private val permissionClass = permDataModelBundle.loadClass("net.corda.permissions.model.Permission")
        private val userPropertyClass = permDataModelBundle.loadClass("net.corda.permissions.model.UserProperty")
        private val groupPropertyClass = permDataModelBundle.loadClass("net.corda.permissions.model.GroupProperty")
        private val changeAuditClass = permDataModelBundle.loadClass("net.corda.permissions.model.ChangeAudit")
        private val roleUserAssociationClass =
            permDataModelBundle.loadClass("net.corda.permissions.model.RoleUserAssociation")
        private val roleGroupAssociationClass =
            permDataModelBundle.loadClass("net.corda.permissions.model.RoleGroupAssociation")
        private val rolePermissionAssociationClass =
            permDataModelBundle.loadClass("net.corda.permissions.model.RolePermissionAssociation")

        @Suppress("unused")
        @JvmStatic
        @BeforeAll
        fun setupEntities() {

            val schemaClass = Schema::class.java
            val bundle = FrameworkUtil.getBundle(schemaClass)
            logger.info("RBAC schema bundle $bundle".emphasise())

            logger.info("Create Schema for ${dbConfig.dataSource.connection.metaData.url}".emphasise())
            val fullName = schemaClass.packageName + ".rbac"
            val resourcePrefix = fullName.replace('.', '/')
            val cl = ClassloaderChangeLog(
                linkedSetOf(
                    ClassloaderChangeLog.ChangeLogResourceFiles(
                        fullName, listOf("$resourcePrefix/migration/rpc-rbac-creation-v1.0.xml"), classLoader = schemaClass.classLoader)
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
                    userClass, groupClass, roleClass, permissionClass, userPropertyClass, groupPropertyClass,
                    changeAuditClass, roleUserAssociationClass, roleGroupAssociationClass, rolePermissionAssociationClass
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

    // Have to do it via reflection to ensure the correct classloader will be used
    private fun createUser(): Any {
        val userCtor = userClass.getDeclaredConstructor(
            String::class.java, Instant::class.java, String::class.java,
            String::class.java, Boolean::class.java, String::class.java, String::class.java, Instant::class.java,
            groupClass
        )
        return userCtor.newInstance(
            "userId", Instant.now(), "fullName", "loginName", true,
            "saltValue", "hashedPassword", null, null
        )
    }

    @Test
    fun `test user creation`() {
        val em = emf.createEntityManager()
        try {
            em.transaction.begin()
            val user = createUser()
            em.persist(user)
            em.transaction.commit()

            val resultList = em.createQuery("from User", userClass).resultList
            assertThat(resultList).contains(user)
        } finally {
            em.close()
        }
    }
}