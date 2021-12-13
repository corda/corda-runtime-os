package net.corda.orm.impl

import net.corda.db.testkit.InMemoryEntityManagerConfiguration
import net.corda.orm.impl.test.entities.Cat
import net.corda.orm.impl.test.entities.Owner
import net.corda.orm.utils.commit
import net.corda.orm.utils.transaction
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.util.UUID

class EntityManagerUtilsTest {

    @Test
    fun `can persist JPA entities using EntityManagerFactory#commit`() {

        val emf = EntityManagerFactoryFactoryImpl().create(
            "cats",
            listOf(Cat::class.java, Owner::class.java),
            InMemoryEntityManagerConfiguration("cats")
        )

        val owner = Owner(UUID.randomUUID(), "Fred", 25)
        val cat = Cat(UUID.randomUUID(), "Jerry", "Black & White", owner)

        emf.commit {
            it.persist(owner)
            it.persist(cat)
        }

        val loadedCats = emf.createEntityManager().createQuery("from Cat", Cat::class.java)
        Assertions.assertThat(loadedCats.resultList).contains(cat)
    }

    @Test
    fun `can persist JPA entities using EntityManagerFactory#transaction`() {

        val emf = EntityManagerFactoryFactoryImpl().create(
            "cats",
            listOf(Cat::class.java, Owner::class.java),
            InMemoryEntityManagerConfiguration("cats")
        )

        val owner = Owner(UUID.randomUUID(), "Fred", 25)
        val cat = Cat(UUID.randomUUID(), "Jerry", "Black & White", owner)

        emf.transaction {
            it.persist(owner)
            it.persist(cat)
            it.transaction.commit()
        }

        val loadedCats = emf.createEntityManager().createQuery("from Cat", Cat::class.java)
        Assertions.assertThat(loadedCats.resultList).contains(cat)
    }

    @Test
    fun `can persist JPA entities using EntityManager#commit`() {

        val emf = EntityManagerFactoryFactoryImpl().create(
            "cats",
            listOf(Cat::class.java, Owner::class.java),
            InMemoryEntityManagerConfiguration("cats")
        )

        val owner = Owner(UUID.randomUUID(), "Fred", 25)
        val cat = Cat(UUID.randomUUID(), "Jerry", "Black & White", owner)

        val em = emf.createEntityManager()
        em.commit {
            it.persist(owner)
            it.persist(cat)
        }

        val loadedCats = emf.createEntityManager().createQuery("from Cat", Cat::class.java)
        Assertions.assertThat(loadedCats.resultList).contains(cat)
    }

    @Test
    fun `can persist JPA entities using EntityManager#transaction`() {

        val emf = EntityManagerFactoryFactoryImpl().create(
            "cats",
            listOf(Cat::class.java, Owner::class.java),
            InMemoryEntityManagerConfiguration("cats")
        )

        val owner = Owner(UUID.randomUUID(), "Fred", 25)
        val cat = Cat(UUID.randomUUID(), "Jerry", "Black & White", owner)

        val em = emf.createEntityManager()
        em.transaction {
            it.persist(owner)
            it.persist(cat)
            it.transaction.commit()
        }

        val loadedCats = emf.createEntityManager().createQuery("from Cat", Cat::class.java)
        Assertions.assertThat(loadedCats.resultList).contains(cat)
    }

    @Test
    fun `can load JPA entities using EntityManagerFactory#commit`() {

        val emf = EntityManagerFactoryFactoryImpl().create(
            "cats",
            listOf(Cat::class.java, Owner::class.java),
            InMemoryEntityManagerConfiguration("cats")
        )

        val owner = Owner(UUID.randomUUID(), "Fred", 25)
        val cat = Cat(UUID.randomUUID(), "Jerry", "Black & White", owner)

        emf.transaction {
            it.persist(owner)
            it.persist(cat)
            it.transaction.commit()
        }

        emf.createEntityManager().commit {
            val loadedCats = it.createQuery("from Cat", Cat::class.java)
            Assertions.assertThat(loadedCats.resultList).contains(cat)
        }
    }

    @Test
    fun `can load JPA entities using EntityManagerFactory#transaction`() {

        val emf = EntityManagerFactoryFactoryImpl().create(
            "cats",
            listOf(Cat::class.java, Owner::class.java),
            InMemoryEntityManagerConfiguration("cats")
        )

        val owner = Owner(UUID.randomUUID(), "Fred", 25)
        val cat = Cat(UUID.randomUUID(), "Jerry", "Black & White", owner)

        emf.transaction {
            it.persist(owner)
            it.persist(cat)
            it.transaction.commit()
        }

        emf.createEntityManager().transaction {
            val loadedCats = it.createQuery("from Cat", Cat::class.java)
            Assertions.assertThat(loadedCats.resultList).contains(cat)
        }
    }

    @Test
    fun `can load JPA entities using EntityManager#commit`() {

        val emf = EntityManagerFactoryFactoryImpl().create(
            "cats",
            listOf(Cat::class.java, Owner::class.java),
            InMemoryEntityManagerConfiguration("cats")
        )

        val owner = Owner(UUID.randomUUID(), "Fred", 25)
        val cat = Cat(UUID.randomUUID(), "Jerry", "Black & White", owner)

        val em = emf.createEntityManager()
        em.transaction {
            it.persist(owner)
            it.persist(cat)
            it.transaction.commit()
        }

        emf.commit {
            val loadedCats = it.createQuery("from Cat", Cat::class.java)
            Assertions.assertThat(loadedCats.resultList).contains(cat)
        }
    }

    @Test
    fun `can load JPA entities using EntityManager#transaction`() {

        val emf = EntityManagerFactoryFactoryImpl().create(
            "cats",
            listOf(Cat::class.java, Owner::class.java),
            InMemoryEntityManagerConfiguration("cats")
        )

        val owner = Owner(UUID.randomUUID(), "Fred", 25)
        val cat = Cat(UUID.randomUUID(), "Jerry", "Black & White", owner)

        val em = emf.createEntityManager()
        em.transaction {
            it.persist(owner)
            it.persist(cat)
            it.transaction.commit()
        }

        emf.transaction {
            val loadedCats = it.createQuery("from Cat", Cat::class.java)
            Assertions.assertThat(loadedCats.resultList).contains(cat)
        }
    }

    @Test
    fun `can't read resultList outside of transaction block`() {

        val emf = EntityManagerFactoryFactoryImpl().create(
            "cats",
            listOf(Cat::class.java, Owner::class.java),
            InMemoryEntityManagerConfiguration("cats")
        )

        val owner = Owner(UUID.randomUUID(), "Fred", 25)
        val cat = Cat(UUID.randomUUID(), "Jerry", "Black & White", owner)

        val em = emf.createEntityManager()
        em.transaction {
            it.persist(owner)
            it.persist(cat)
            it.transaction.commit()
        }

        val loadedCats = emf.transaction { it.createQuery("from Cat", Cat::class.java) }
        Assertions.assertThatThrownBy { loadedCats.resultList }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("closed")
    }
}