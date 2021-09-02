package net.corda.db.test.osgi

import net.corda.orm.EntityManagerFactoryFactory
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.impl.InMemoryEntityManagerConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.framework.FrameworkUtil
import org.osgi.test.junit5.service.ServiceExtension
import java.time.LocalDate
import java.util.UUID

@ExtendWith(ServiceExtension::class)
class EntitiesInBundlesTest {
    companion object {
        val DOG_CLASS_NAME = "net.corda.testing.bundles.dogs.Dog"
        val CAT_CLASS_NAME = "net.corda.testing.bundles.cats.Cat"
        val OWNER_CLASS_NAME = "net.corda.testing.bundles.cats.Owner"
    }

    /**
     * This test is here to prove that we can persist JPA annotated entities from separate bundles.
     * This means we get the bundles at runtime and create instances of these entities using
     * reflection.
     * This is not a suggestion of good practise, but proves out something a DB worker may do
     * when processing a serialised entity.
     *
     */
    @Test
    fun `use entityManager to persist JPA entities from separate bundles`() {
        // get entities from bundles at runtime
        val dogBundle =
            FrameworkUtil.getBundle(Class.forName(DOG_CLASS_NAME))
        val dogClass = dogBundle.loadClass(DOG_CLASS_NAME)
        println(dogClass)
//        val dogCtor = dogClass.getDeclaredConstructor(UUID::class.java, String::class.java, LocalDate::class.java)
//
//        val catBundle = FrameworkUtil.getBundle(Class.forName(CAT_CLASS_NAME))
//        val ownerClass = catBundle.loadClass(OWNER_CLASS_NAME)
//        val catClass = catBundle.loadClass(CAT_CLASS_NAME)
//        val ownerCtor = ownerClass.getDeclaredConstructor(UUID::class.java, String::class.java, Int::class.java)
//        val catCtor = catClass.getDeclaredConstructor(UUID::class.java, String::class.java, String::class.java, ownerClass)
//
//        val dog = dogCtor.newInstance(UUID.randomUUID(), "Faraway", LocalDate.of(2020, 2, 26))
//        val owner = ownerCtor.newInstance(UUID.randomUUID(), "Bob", 26)
//        val cat = catCtor.newInstance(UUID.randomUUID(), "Stray", "Tabby", owner)
//
//        // TODO: should EntityManagerFactoryFactory be injected as an OSGi service?
//        val emff: EntityManagerFactoryFactory = EntityManagerFactoryFactoryImpl()
//        val emf = emff.createEntityManagerFactory(
//            "pets",
//            listOf(catClass, ownerClass, dogClass),
//            // TODO: use Postgres
//            InMemoryEntityManagerConfiguration("pets")
//        )
//        val em = emf.createEntityManager()
//        em.transaction.begin()
//        em.persist(dog)
//        em.persist(owner)
//        em.persist(cat)
//        em.transaction.commit()
//        em.flush()
//
//        assertThat(em.createQuery("from Cat", catClass).resultList).isNotEmpty()
//        assertThat(em.createQuery("from Dog", dogClass).resultList).isNotEmpty()
    }
}
