package net.corda.orm.impl

import net.corda.db.testkit.InMemoryEntityManagerConfiguration
import net.corda.orm.impl.test.entities.Cat
import net.corda.orm.impl.test.entities.MutableEntity
import net.corda.orm.impl.test.entities.Owner
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID
import javax.persistence.EntityManagerFactory

class EntityManagerUtilsTest {

    @Test
    fun `can persist JPA entities using EntityManagerFactory#transaction`() {

        createEntityManagerFactory().use { emf ->

            val owner = Owner(UUID.randomUUID(), "Fred", 25)
            val cat = Cat(UUID.randomUUID(), "Tom", "Black & White", owner)

            emf.createEntityManager().transaction {
                it.persist(owner)
                it.persist(cat)
            }

            val loadedCats = emf.createEntityManager().createQuery("from Cat", Cat::class.java)
            Assertions.assertThat(loadedCats.resultList).contains(cat)
        }
    }

    @Test
    fun `can persist JPA entities using EntityManagerFactory#use`() {

        createEntityManagerFactory().use { emf ->

            val owner = Owner(UUID.randomUUID(), "Fred", 25)
            val cat = Cat(UUID.randomUUID(), "Tom", "Black & White", owner)

            emf.createEntityManager().use {
                it.transaction.begin()
                it.persist(owner)
                it.persist(cat)
                it.transaction.commit()
            }

            val loadedCats = emf.createEntityManager().createQuery("from Cat", Cat::class.java)
            Assertions.assertThat(loadedCats.resultList).contains(cat)
        }
    }

    @Test
    fun `can persist JPA entities using EntityManager#transaction`() {

        createEntityManagerFactory().use { emf ->

            val owner = Owner(UUID.randomUUID(), "Fred", 25)
            val cat = Cat(UUID.randomUUID(), "Tom", "Black & White", owner)

            val em = emf.createEntityManager()
            em.transaction {
                it.persist(owner)
                it.persist(cat)
            }

            val loadedCats = emf.createEntityManager().createQuery("from Cat", Cat::class.java)
            Assertions.assertThat(loadedCats.resultList).contains(cat)
        }
    }

    @Test
    fun `can persist JPA entities using EntityManager#use`() {

        createEntityManagerFactory().use { emf ->

            val owner = Owner(UUID.randomUUID(), "Fred", 25)
            val cat = Cat(UUID.randomUUID(), "Tom", "Black & White", owner)

            val em = emf.createEntityManager()
            em.use {
                it.transaction.begin()
                it.persist(owner)
                it.persist(cat)
                it.transaction.commit()
            }

            val loadedCats = emf.createEntityManager().createQuery("from Cat", Cat::class.java)
            Assertions.assertThat(loadedCats.resultList).contains(cat)
        }
    }

    @Test
    fun `can load JPA entities using EntityManagerFactory#transaction`() {

        createEntityManagerFactory().use { emf ->

            val owner = Owner(UUID.randomUUID(), "Fred", 25)
            val cat = Cat(UUID.randomUUID(), "Tom", "Black & White", owner)

            emf.transaction {
                it.persist(owner)
                it.persist(cat)
            }

            emf.createEntityManager().transaction {
                val loadedCats = it.createQuery("from Cat", Cat::class.java)
                Assertions.assertThat(loadedCats.resultList).contains(cat)
            }
        }
    }

    @Test
    fun `can load JPA entities using EntityManagerFactory#use`() {
        createEntityManagerFactory().use { emf ->

            val owner = Owner(UUID.randomUUID(), "Fred", 25)
            val cat = Cat(UUID.randomUUID(), "Tom", "Black & White", owner)

            emf.createEntityManager().use {
                it.transaction.begin()
                it.persist(owner)
                it.persist(cat)
                it.transaction.commit()
            }

            emf.createEntityManager().use {
                val loadedCats = it.createQuery("from Cat", Cat::class.java)
                Assertions.assertThat(loadedCats.resultList).contains(cat)
            }
        }
    }

    @Test
    fun `can load JPA entities using EntityManager#transaction`() {

        createEntityManagerFactory().use { emf ->

            val owner = Owner(UUID.randomUUID(), "Fred", 25)
            val cat = Cat(UUID.randomUUID(), "Tom", "Black & White", owner)

            val em = emf.createEntityManager()
            em.use {
                it.transaction.begin()
                it.persist(owner)
                it.persist(cat)
                it.transaction.commit()
            }

            emf.transaction {
                val loadedCats = it.createQuery("from Cat", Cat::class.java)
                Assertions.assertThat(loadedCats.resultList).contains(cat)
            }
        }
    }

    @Test
    fun `can load JPA entities using EntityManager#use`() {

        createEntityManagerFactory().use { emf ->

            val owner = Owner(UUID.randomUUID(), "Fred", 25)
            val cat = Cat(UUID.randomUUID(), "Tom", "Black & White", owner)

            val em = emf.createEntityManager()
            em.use {
                it.transaction.begin()
                it.persist(owner)
                it.persist(cat)
                it.transaction.commit()
            }

            emf.createEntityManager().use {
                val loadedCats = it.createQuery("from Cat", Cat::class.java)
                Assertions.assertThat(loadedCats.resultList).contains(cat)
            }
        }
    }

    @Test
    fun `can't read resultList outside of use block`() {

        createEntityManagerFactory().use { emf ->

            val owner = Owner(UUID.randomUUID(), "Fred", 25)
            val cat = Cat(UUID.randomUUID(), "Tom", "Black & White", owner)

            val em = emf.createEntityManager()
            em.use {
                it.transaction.begin()
                it.persist(owner)
                it.persist(cat)
                it.transaction.commit()
            }

            val loadedCats = emf.createEntityManager().use { it.createQuery("from Cat", Cat::class.java) }
            Assertions.assertThatThrownBy { loadedCats.resultList }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("closed")
        }
    }

    @Test
    fun `can't read resultList outside of transaction block`() {

        createEntityManagerFactory().use { emf ->

            val owner = Owner(UUID.randomUUID(), "Fred", 25)
            val cat = Cat(UUID.randomUUID(), "Tom", "Black & White", owner)

            val em = emf.createEntityManager()
            em.use {
                it.transaction.begin()
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

    @Suppress("TooGenericExceptionThrown")
    @Test
    fun `EntityManager#transaction rolls back the transaction if an exception is thrown`() {
        val expectedTag = "initialTag"
        val entity = MutableEntity(UUID.randomUUID(), expectedTag)

        createEntityManagerFactory().use { emf ->
            emf.transaction { em ->
                em.persist(entity)
            }

            try {
                emf.transaction { em ->
                    val retrievedEntity = em.find(MutableEntity::class.java, entity.id)
                    retrievedEntity.tag = "newTag"
                    throw Exception()
                }
            } catch (_: Exception) {
            }

            val retrievedEntity = emf.transaction { em ->
                em.find(MutableEntity::class.java, entity.id)
            }

            assertEquals(expectedTag, retrievedEntity.tag)
        }
    }

    private fun createEntityManagerFactory(): EntityManagerFactory {
        return EntityManagerFactoryFactoryImpl().create(
            "cats",
            listOf(Cat::class.java, Owner::class.java, MutableEntity::class.java),
            InMemoryEntityManagerConfiguration("cats")
        )
    }
}