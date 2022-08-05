package net.corda.testutils.services

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.types.MemberX500Name
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
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
