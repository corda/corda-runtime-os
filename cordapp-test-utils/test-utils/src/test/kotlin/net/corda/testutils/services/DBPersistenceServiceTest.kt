package net.corda.testutils.services

import net.corda.v5.application.persistence.find
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.types.MemberX500Name
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

class DBPersistenceServiceTest {


    private val x500 = MemberX500Name.parse("CN=IRunCorDapps, OU=Application, O=R3, L=London, C=GB")

    @Test
    fun `should connect using hsqldb as default`() {
        // Given a persistence service for a member
        val persistence = DBPersistenceService(x500)

        // When we persist two entities
        persistence.persist(GreetingEntity(UUID.randomUUID(), "Hello!"))
        persistence.persist(GreetingEntity(UUID.randomUUID(), "Bonjour!"))

        // Then we should be able to retrieve them again
        val greetings = persistence.findAll(GreetingEntity::class.java).execute()
        assertThat(greetings.map{ it.greeting }.toSet(), `is`(setOf("Hello!", "Bonjour!")))
    }

    @Test
    fun `should ensure that one member cannot see the db of another`() {
        // Given two members with dbs
        val x5002 = MemberX500Name.parse("CN=MeToo, OU=Application, O=R3, L=London, C=GB")

        val persistence1 = DBPersistenceService(x500)
        val persistence2 = DBPersistenceService(x5002)

        // When we persist an entity to one of them
        persistence1.persist(GreetingEntity(UUID.randomUUID(), "Hello!"))

        // Then it should not show up in the other one
        val greetings = persistence2.findAll(GreetingEntity::class.java).execute()
        assertThat("Greetings should be empty", greetings.isEmpty())
    }

    @Test
    fun `should always use the same db for any given member`() {
        // Given a member with two instances of the persistence service
        val persistence1 = DBPersistenceService(x500)
        val persistence2 = DBPersistenceService(x500)

        // When we persist an entity to one of them
        val hello = GreetingEntity(UUID.randomUUID(), "Hello!")
        persistence1.persist(hello)

        // Then it should show up in the other one
        val greetings = persistence2.findAll(GreetingEntity::class.java).execute()
        assertThat(greetings, `is`(listOf(hello)))
    }

    @Test
    fun `should provide merge and find methods`() {
        val persistence = DBPersistenceService(x500)

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
    }

    @Test
    fun `should provide list merge and find methods`() {
        val persistence = DBPersistenceService(x500)

        // Given persisted greetings
        val hello = GreetingEntity(UUID.randomUUID(), "Hello!")
        val gutentag = GreetingEntity(UUID.randomUUID(), "Guten Tag!")

        persistence.persist(listOf(hello, gutentag))

        // When we merge then find them again
        val goodEve = GreetingEntity(hello.id, "Good evening!")
        val gutenAbend = GreetingEntity(gutentag.id, "Guten Abend!")

        val merged = persistence.merge(listOf(goodEve, gutenAbend))
        val retrievedHellos : List<GreetingEntity> = persistence.find(listOf(hello.id, gutentag.id))

        // Then they should be the merged versions
        assertThat(retrievedHellos.toSet(), `is`(setOf(goodEve, gutenAbend)))
        assertThat(merged.toSet(), `is`(setOf(goodEve, gutenAbend)))
    }

    @Test
    fun `should provide remove methods`() {
        val persistence = DBPersistenceService(x500)


        // Given persisted greetings
        val greetings = listOf("Hello!", "Guten Tag!", "Bonjour!", "こんにちは").map {
            GreetingEntity(UUID.randomUUID(), it)
        }

        persistence.persist(greetings)

        // When we remove three of them
        persistence.remove(greetings[0])
        persistence.remove(listOf(greetings[1], greetings[2]))

        // Then they should be removed successfully
        assertThat(persistence.findAll(GreetingEntity::class.java).execute(), `is`(listOf(greetings[3])))
    }
}

@CordaSerializable
@Entity
data class GreetingEntity (
    @Id
    @Column
    val id: UUID,
    @Column
    val greeting: String
)
