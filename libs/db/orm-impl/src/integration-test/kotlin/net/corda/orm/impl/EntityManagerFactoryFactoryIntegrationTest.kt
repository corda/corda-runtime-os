package net.corda.orm.impl

import net.corda.orm.impl.test.entities.Cat
import net.corda.orm.impl.test.entities.Owner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class EntityManagerFactoryFactoryIntegrationTest {
    @Test
    fun `can persist JPA entity`() {

        val emf = EntityManagerFactoryFactoryImpl().createEntityManagerFactory(
            "cats",
            listOf(Cat::class.java, Owner::class.java),
            InMemoryEntityManagerConfiguration("cats")
        )

        val owner = Owner(UUID.randomUUID(), "Fred", 25)
        val cat = Cat(UUID.randomUUID(), "Jerry", "Black & White", owner)

        val em = emf.createEntityManager()
        em.transaction.begin()
        em.persist(owner)
        em.persist(cat)
        em.transaction.commit()

        val loadedCats = em.createQuery("from Cat", Cat::class.java)

        assertThat(loadedCats.resultList).contains(cat)
    }
}
