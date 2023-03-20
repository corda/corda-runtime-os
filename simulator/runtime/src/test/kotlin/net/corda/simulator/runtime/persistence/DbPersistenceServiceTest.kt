package net.corda.simulator.runtime.persistence

import net.corda.v5.application.persistence.CordaPersistenceException
import net.corda.v5.base.types.MemberX500Name
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.fail
import java.util.UUID

class DbPersistenceServiceTest {

    private val member = MemberX500Name.parse("CN=IRunCorDapps, OU=Application, O=R3, L=London, C=GB")

    @Test
    fun `should connect using hsqldb as default`() {
        // Given a persistence service for a member
        val persistence = DbPersistenceService(member)

        // When we persist two entities
        persistence.persist(GreetingEntity(UUID.randomUUID(), "Hello!"))
        persistence.persist(GreetingEntity(UUID.randomUUID(), "Bonjour!"))

        // Then we should be able to retrieve them again
        val greetings = persistence.findAll(GreetingEntity::class.java).execute()
        assertThat(greetings.map{ it.greeting }.toSet(), `is`(setOf("Hello!", "Bonjour!")))

        persistence.close()
    }

    @Test
    fun `should ensure that one member cannot see the db of another`() {
        // Given two members with dbs
        val member2 = MemberX500Name.parse("CN=MeToo, OU=Application, O=R3, L=London, C=GB")

        val persistence1 = DbPersistenceService(member)
        val persistence2 = DbPersistenceService(member2)

        // When we persist an entity to one of them
        persistence1.persist(GreetingEntity(UUID.randomUUID(), "Hello!"))

        // Then it should not show up in the other one
        val greetings = persistence2.findAll(GreetingEntity::class.java).execute()
        assertThat("Greetings should be empty", greetings.isEmpty())

        persistence1.close()
        persistence2.close()
    }

    @Test
    fun `should be able to close a persistence service`() {
        // Given a db service we've closed
        val persistence = DbPersistenceService(member)
        persistence.close()

        // When we try to save something in it
        // Then it should throw an error
        assertThrows<CordaPersistenceException>{
            persistence.persist(GreetingEntity(UUID.randomUUID(),"Hello!"))
        }
    }

    @Test
    fun `should always use the same db for any given member`() {
        // Given a member with two instances of the persistence service
        val persistence1 = DbPersistenceService(member)
        val persistence2 = DbPersistenceService(member)

        // When we persist an entity to one of them
        val hello = GreetingEntity(UUID.randomUUID(), "Hello!")
        persistence1.persist(hello)

        // Then it should show up in the other one
        val greetings = persistence2.findAll(GreetingEntity::class.java).execute()
        assertThat(greetings, `is`(listOf(hello)))

        persistence1.close()
        persistence2.close()
    }

    @Test
    fun `should provide merge and find methods`() {
        val persistence = DbPersistenceService(member)

        // Given a persisted greeting
        val hello = GreetingEntity(UUID.randomUUID(), "Hello!")
        persistence.persist(hello)

        // When we merge then find it again
        val bonjour = GreetingEntity(hello.id, "Bonjour!")
        val merged = persistence.merge(bonjour)

        val retrievedHello : GreetingEntity = persistence.find(GreetingEntity::class.java, hello.id) ?:
            fail("Could not find ${GreetingEntity::class.java.simpleName} with id ${hello.id}")

        // Then it should be the merged version
        assertThat(retrievedHello, `is`(bonjour))
        assertThat(merged, `is`(bonjour))

        persistence.close()
    }

    @Test
    fun `should provide list merge and find methods`() {
        val persistence = DbPersistenceService(member)

        // Given persisted greetings
        val hello = GreetingEntity(UUID.randomUUID(), "Hello!")
        val gutentag = GreetingEntity(UUID.randomUUID(), "Guten Tag!")

        persistence.persist(listOf(hello, gutentag))

        // When we merge then find them again
        val goodEve = GreetingEntity(hello.id, "Good evening!")
        val gutenAbend = GreetingEntity(gutentag.id, "Guten Abend!")

        val merged = persistence.merge(listOf(goodEve, gutenAbend))
        val retrievedHellos = persistence.find(GreetingEntity::class.java, listOf(hello.id, gutentag.id))

        // Then they should be the merged versions
        assertThat(retrievedHellos.toSet(), `is`(setOf(goodEve, gutenAbend)))
        assertThat(merged.toSet(), `is`(setOf(goodEve, gutenAbend)))

        persistence.close()
    }

    @Test
    fun `should provide findAll and remove methods`() {
        val persistence = DbPersistenceService(member)

        // Given persisted greetings
        val greetings = (1..12).map {
            GreetingEntity(UUID.randomUUID(), "Hello$it")
        }

        persistence.persist(greetings)

        // When we remove three of them
        persistence.remove(greetings[9])
        persistence.remove(listOf(greetings[10], greetings[11]))

        // Then they should be removed successfully
        val retrieved1 = persistence.findAll(GreetingEntity::class.java)
            .setLimit(5)
            .execute()

        val retrieved2 = persistence.findAll(GreetingEntity::class.java)
            .setOffset(5)
            .setLimit(4)
            .execute()

        // Then they should all be present
        assertThat(retrieved1.plus(retrieved2).sortedBy { it.greeting }, `is`(greetings.subList(0, 9)))

        persistence.close()
    }

    @Test
    fun `should support named queries`() {
        val persistence = DbPersistenceService(member)

        // Given persisted greetings
        val greetings = (1..9).map {
            GreetingEntity(UUID.randomUUID(), "Hello$it")
        }

        persistence.persist(greetings)

        // When we find them via a named query
        val retrieved1 = persistence.query("Greetings.findAll", GreetingEntity::class.java)
            .setLimit(5)
            .execute()

        val retrieved2 = persistence.query("Greetings.findAll", GreetingEntity::class.java)
            .setOffset(5)
            .setLimit(4)
            .execute()

        // Then they should all be present
        assertThat(retrieved1.plus(retrieved2).sortedBy { it.greeting }, `is`(greetings))

        persistence.close()
    }
}

