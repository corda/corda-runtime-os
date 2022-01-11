package net.corda.orm.impl

import net.corda.db.testkit.InMemoryEntityManagerConfiguration
import net.corda.orm.impl.test.entities.Cat
import net.corda.orm.impl.test.entities.Owner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class EntityManagerFactoryFactoryIntegrationTest {
    @Test
    fun `can persist JPA entity`() {

        val emf = EntityManagerFactoryFactoryImpl().create(
            "cats",
            listOf(Cat::class.java, Owner::class.java),
            InMemoryEntityManagerConfiguration("cats")
        )

        val owner = Owner(UUID.randomUUID(), "Fred", 25)
        val cat = Cat(UUID.randomUUID(), "Tom", "Black & White", owner)

        val em = emf.createEntityManager()
        em.transaction.begin()
        em.persist(owner)
        em.persist(cat)
        em.transaction.commit()

        val loadedCats = em.createQuery("from Cat", Cat::class.java)

        assertThat(loadedCats.resultList).contains(cat)
    }

    @Test
    fun `can named query`() {

        val emf = EntityManagerFactoryFactoryImpl().create(
            "cats",
            listOf(Cat::class.java, Owner::class.java),
            InMemoryEntityManagerConfiguration("cats")
        )

        val owner1 = Owner(UUID.randomUUID(), "Amy", 25)
        val owner2 = Owner(UUID.randomUUID(), "Francis", 47)
        val cat1 = Cat(UUID.randomUUID(), "Felix", "Black & White", owner1)
        val cat2 = Cat(UUID.randomUUID(), "Thomas", "Tabby", owner1)
        val cat3 = Cat(UUID.randomUUID(), "Christopher", "Ginger", owner2)

        val em = emf.createEntityManager()
        em.transaction.begin()
        em.persist(owner1)
        em.persist(owner2)
        em.persist(cat1)
        em.persist(cat2)
        em.persist(cat3)
        em.transaction.commit()

        val loadedCats = em
            .createNamedQuery("Cat.findByOwner", String()::class.java)
            .setParameter("owner", "Amy")

        assertThat(loadedCats.resultList).containsExactlyInAnyOrder("Felix", "Thomas")
    }
}
