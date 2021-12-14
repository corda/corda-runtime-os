package net.corda.db.test.osgi

import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.LiquibaseSchemaMigrator.Companion.PUBLIC_SCHEMA
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.testkit.DbUtils.getEntityManagerConfiguration
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.orm.utils.transaction
import net.corda.test.util.LoggingUtils.emphasise
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.framework.FrameworkUtil
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.StringWriter
import java.time.LocalDate
import java.util.UUID
import javax.persistence.EntityManagerFactory

/**
 * These tests are here to prove that we can persist JPA annotated entities from separate bundles.
 * This means we get the bundles at runtime and create instances of these entities using
 * reflection.
 * This is not a suggestion of good practise, but proves out something a DB worker may do
 * when processing a serialised entity.
 *
 */
@ExtendWith(ServiceExtension::class)
class EntitiesInBundlesTest {
    companion object {
        private const val DOG_CLASS_NAME = "net.corda.testing.bundles.dogs.Dog"
        private const val CAT_CLASS_NAME = "net.corda.testing.bundles.cats.Cat"
        private const val OWNER_CLASS_NAME = "net.corda.testing.bundles.cats.Owner"

        private val logger: Logger = LoggerFactory.getLogger("TEST")

        @InjectService
        lateinit var entityManagerFactoryFactory: EntityManagerFactoryFactory
        @InjectService
        lateinit var lbm: LiquibaseSchemaMigrator

        lateinit var emf: EntityManagerFactory

        private val dogBundle = run {
            val bundle = FrameworkUtil.getBundle(this::class.java).bundleContext.bundles.single { bundle ->
                bundle.symbolicName == "net.corda.testing-dogs"
            }
            logger.info("Dog bundle $bundle".emphasise())
            bundle
        }

        private val catBundle = run {
            val bundle = FrameworkUtil.getBundle(this::class.java).bundleContext.bundles.single { bundle ->
                bundle.symbolicName == "net.corda.testing-cats"
            }
            logger.info("Cat bundle $bundle".emphasise())
            bundle
        }

        private val dogClass = dogBundle.loadClass(DOG_CLASS_NAME)
        private val ownerClass = catBundle.loadClass(OWNER_CLASS_NAME)
        private val catClass = catBundle.loadClass(CAT_CLASS_NAME)

        private val dogCtor = dogClass.getDeclaredConstructor(UUID::class.java, String::class.java, LocalDate::class.java, String::class.java)
        private val ownerCtor = ownerClass.getDeclaredConstructor(UUID::class.java, String::class.java, Int::class.java)
        private val catCtor = catClass.getDeclaredConstructor(UUID::class.java, String::class.java, String::class.java, ownerClass)

        private val dogId = UUID.randomUUID()
        private val dog = dogCtor.newInstance(dogId, "Faraway", LocalDate.of(2020, 2, 26), "Bob")
        private val ownerId = UUID.randomUUID()
        private val owner = ownerCtor.newInstance(ownerId, "Bob", 26)
        private val catId = UUID.randomUUID()
        private val cat = catCtor.newInstance(catId, "Stray", "Tabby", owner)

        private val dbConfig: EntityManagerConfiguration = getEntityManagerConfiguration("pets")

        @Suppress("unused")
        @JvmStatic
        @BeforeAll
        fun setupEntities() {
            logger.info("Create Schema for ${dbConfig.dataSource.connection.metaData.url}".emphasise())
            val cl = ClassloaderChangeLog(
                linkedSetOf(
                    ClassloaderChangeLog.ChangeLogResourceFiles(
                        catClass.packageName, listOf("migration/db.changelog-master.xml"), classLoader = catClass.classLoader),
                    ClassloaderChangeLog.ChangeLogResourceFiles(
                        dogClass.packageName, listOf("migration/db.changelog-master.xml"), classLoader = dogClass.classLoader)
            ))
            StringWriter().use {
                lbm.createUpdateSql(dbConfig.dataSource.connection, cl, it, PUBLIC_SCHEMA)
                logger.info("Schema creation SQL: $it")
            }
            lbm.updateDb(dbConfig.dataSource.connection, cl, PUBLIC_SCHEMA)

            logger.info("Create Entities".emphasise())

            emf = entityManagerFactoryFactory.create(
                "pets",
                listOf(catClass, ownerClass, dogClass),
                dbConfig
            )
            emf.transaction { em ->
                em.persist(dog)
                em.persist(owner)
                em.persist(cat)
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
            assertNotSame(catClass, lazyCat::class.java)
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