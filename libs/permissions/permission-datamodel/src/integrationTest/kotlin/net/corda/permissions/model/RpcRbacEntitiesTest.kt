package net.corda.permissions.model

import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.schema.Schema
import net.corda.db.testkit.DbUtils.getEntityManagerConfiguration
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.orm.impl.InMemoryEntityManagerConfiguration
import net.corda.test.util.LoggingUtils.emphasise
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.junit5.service.ServiceExtension
import java.io.StringWriter
import java.time.LocalDate
import java.util.*
import javax.persistence.EntityManagerFactory
import net.corda.v5.base.util.contextLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Order
import org.osgi.framework.FrameworkUtil
import org.osgi.test.common.annotation.InjectService


@ExtendWith(ServiceExtension::class)
class RpcRbacEntitiesTest {
    companion object {


        private val logger = contextLogger()

        @InjectService
        lateinit var entityManagerFactoryFactory: EntityManagerFactoryFactory
        @InjectService
        lateinit var lbm: LiquibaseSchemaMigrator

        lateinit var emf: EntityManagerFactory

        private val dbConfig: EntityManagerConfiguration = getEntityManagerConfiguration() ?: run {
            logger.info("Using in-memory (HSQL) DB".emphasise())
            InMemoryEntityManagerConfiguration("rbac")
        }

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
                listOf(catClass, ownerClass, dogClass),
                dbConfig
            )
            val em = emf.createEntityManager()
            try {
                em.transaction.begin()
                em.persist(dog)
                em.persist(owner)
                em.persist(cat)
                em.transaction.commit()
            } finally {
                em.close()
            }
        }

        @Suppress("unused")
        @AfterAll
        @JvmStatic
        fun done() {
            emf.close()
        }
    }

    @Test
    @Order(1)
    fun `test user creation`() {

    }

    @Test
    fun `confirm dog and cats are not available to this bundle`() {
        assertThrows<ClassNotFoundException> {
            Class.forName(DOG_CLASS_NAME, true, this::class.java.classLoader)
        }

        assertThrows<ClassNotFoundException> {
            Class.forName(CAT_CLASS_NAME, true, this::class.java.classLoader)
        }
    }

    @Test
    fun `validate entities are persisted`() {
        logger.info("Load persisted entities".emphasise())

        val em = emf.createEntityManager()
        try {
            assertThat(
                em.createQuery("from Cat", catClass).resultList
            ).contains(cat)
            assertThat(
                em.createQuery("from Dog", dogClass).resultList
            ).contains(dog)
        } finally {
            em.close()
        }
    }

    @Test
    fun `check we can create lazy proxies`() {
        val em = emf.createEntityManager()
        try {
            val lazyCat = em.getReference(catClass, catId)
            Assertions.assertNotSame(catClass, lazyCat::class.java)
            assertEquals(cat, lazyCat)
        } finally {
            em.close()
        }
    }

    @Test
    fun `confirm we can query cross-bundle`() {
        /** NOTE:
         * This shows that we can create a JPA query that crosses multiple bundles.
         * This isn't a best practise example, and this example is a bit silly, but
         * its only purpose is to prove we can do this.
         */
        logger.info("Query cross-bundle entities".emphasise())

        val em = emf.createEntityManager()
        try {
            // finding an owner in the cats bundle based on a string value from the Dog class in the dogs bundle
            //  note: not a realistic real world query!
            val queryResults = em
                .createQuery("select o.age from Owner as o where o.name in (select d.owner from Dog as d where d.name = :dog)")
                .setParameter("dog", "Faraway")
                .resultList
            logger.info("Age(s) of owners with the same name as \"Faraway's\" (dog) owner: $queryResults".emphasise())
            assertThat(queryResults).contains(26)
        } finally {
            em.close()
        }
    }
}