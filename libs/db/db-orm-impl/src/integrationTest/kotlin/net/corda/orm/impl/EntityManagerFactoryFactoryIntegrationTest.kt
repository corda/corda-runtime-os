package net.corda.orm.impl

import net.corda.db.core.CloseableDataSource
import net.corda.db.core.InMemoryDataSourceFactory
import net.corda.db.testkit.InMemoryEntityManagerConfiguration
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.impl.test.entities.Cat
import net.corda.orm.impl.test.entities.Owner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.*

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

    @Test
    fun `when EntityManagerFactory is closed, CloseableDataSource is closed as well`() {
        val closeableDataSourceMock = mock<CloseableDataSource>()
        class WrappedDataSource(delegate: CloseableDataSource): CloseableDataSource by delegate {
            override fun close() {
                closeableDataSourceMock.close()
            }
        }
        val closeableDataSource = WrappedDataSource(InMemoryDataSourceFactory().create("cats"))
        val entityManagerConfiguration = object: EntityManagerConfiguration {
            override val dataSource: CloseableDataSource
                get() = closeableDataSource
        }
        val emf = EntityManagerFactoryFactoryImpl().create(
            "test",
            listOf(Cat::class.java, Owner::class.java),
            entityManagerConfiguration
        )
        emf.close()
        verify(closeableDataSourceMock).close()
    }

}
