package net.corda.db.test.osgi

import net.corda.orm.DdlManage
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.impl.InMemoryEntityManagerConfiguration
import net.corda.orm.impl.PostgresEntityManagerConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Reference
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID
import kotlin.math.ceil

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
        const val DOG_CLASS_NAME = "net.corda.testing.bundles.dogs.Dog"
        const val CAT_CLASS_NAME = "net.corda.testing.bundles.cats.Cat"
        const val OWNER_CLASS_NAME = "net.corda.testing.bundles.cats.Owner"

        private val logger: Logger = LoggerFactory.getLogger("TEST")

        @InjectService
        lateinit var entityManagerFactoryFactory: EntityManagerFactoryFactory

        private val dogBundle = run {
            val bundle = FrameworkUtil.getBundle(this::class.java).bundleContext.bundles.single { bundle ->
                bundle.symbolicName == "net.corda.dogs"
            }
            logger.info("Dog bundle $bundle".emphasise())
            bundle
        }

        private val catBundle = run {
            val bundle = FrameworkUtil.getBundle(this::class.java).bundleContext.bundles.single { bundle ->
                bundle.symbolicName == "net.corda.cats"
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

        private val dog = dogCtor.newInstance(UUID.randomUUID(), "Faraway", LocalDate.of(2020, 2, 26), "Bob")
        private val owner = ownerCtor.newInstance(UUID.randomUUID(), "Bob", 26)
        private val cat = catCtor.newInstance(UUID.randomUUID(), "Stray", "Tabby", owner)

        private val dbConfig = run {
            if (null != System.getProperty("postgresPort").toIntOrNull()) {
                logger.info("Using Postgres on port ${System.getProperty("postgresPort")}".emphasise())
                PostgresEntityManagerConfiguration(
                    "jdbc:postgresql://localhost:${System.getProperty("postgresPort")}/postgres",
                    "postgres",
                    "password",
                    DdlManage.UPDATE,
                    formatSql = true,
                    showSql = true
                )
            } else {
                logger.info("Using in-memory (HSQL) DB".emphasise())
                InMemoryEntityManagerConfiguration("pets")
            }
        }

        @JvmStatic
        @BeforeAll
        fun setupEntities() {
            logger.info("Create Entities".emphasise())

            val emf = entityManagerFactoryFactory.create(
                "pets",
                listOf(catClass, ownerClass, dogClass),
                dbConfig
            )
            val em = emf.createEntityManager()
            em.transaction.begin()
            em.persist(dog)
            em.persist(owner)
            em.persist(cat)
            em.transaction.commit()
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

        val emf = entityManagerFactoryFactory.create(
            "pets",
            listOf(catClass, ownerClass, dogClass),
            dbConfig
        )
        val em = emf.createEntityManager()

        assertThat(
            em.createQuery("from Cat", catClass).resultList
        ).contains(cat)
        assertThat(
            em.createQuery("from Dog", dogClass).resultList
        ).contains(dog)
    }

    @Test
    fun `confirm we can query cross-bundle`() {
        /** NOTE:
         * This shows that we can create a JPA query that crosses multiple bundles.
         * This isn't a best practise example, and this example is a bit silly, but
         * its only purpose is to prove we can do this.
         */
        logger.info("Query cross-bundle entities".emphasise())

        val emf = entityManagerFactoryFactory.create(
            "pets",
            listOf(catClass, ownerClass, dogClass),
            dbConfig
        )
        val em = emf.createEntityManager()

        // finding an owner in the cats bundle based on a string value from the Dog class in the dogs bundle
        //  note: not a realistic real world query!
        val queryResults = em
            .createQuery("select o.age from Owner as o where o.name in (select d.owner from Dog as d where d.name = :dog)")
            .setParameter("dog", "Faraway")
            .resultList
        logger.info("Age(s) of owners with the same name as \"Faraway's\" (dog) owner: $queryResults".emphasise())
        assertThat(queryResults).contains(26)
    }
}

// trying to make it easy to find the print lines in the very verbose osgi test logging
private fun String.emphasise(paddingChars: String = "#", width: Int = 80): String {
    val padding = paddingChars.repeat(
        kotlin.math.max(ceil((width - this.length - 2).toDouble() / 2).toInt(), 4)
    )
    return "$padding $this $padding"
}
