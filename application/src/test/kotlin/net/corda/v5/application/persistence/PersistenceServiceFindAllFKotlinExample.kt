@file:Suppress("MatchingDeclarationName", "Unused")

package net.corda.v5.application.persistence

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.base.annotations.CordaSerializable
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.NamedQuery
import javax.persistence.Table

@CordaInject
lateinit var persistenceService: PersistenceService

// create a named query setting parameters one-by-one, that returns the second page of up to 100 records
val pagedQuery = persistenceService
    .query("find_by_name_and_age", Dog::class.java)
    .setParameter("name", "Felix")
    .setParameter("maxAge", 5)
    .setLimit(100)
    .setOffset(200)

// execute the query and return the results as a List
val result1: List<Dog> = pagedQuery.execute()

// create a named query setting parameters as Map, that returns the second page of up to 100 records
val paramQuery = persistenceService
    .query("find_by_name_and_age", Dog::class.java)
    .setParameters(mapOf(Pair("name", "Felix"), Pair("maxAge", 5)))
    .setLimit(100)
    .setOffset(200)

// execute the query and return the results as a List
val result2: List<Dog> = pagedQuery.execute()

// For JPA Entity:
@CordaSerializable
@Entity
@Table(name = "DOGS")
@NamedQuery(name = "find_by_name_and_age", query = "SELECT d FROM Dog d WHERE d.name = :name AND d.age <= :maxAge")
class Dog {
    @Id
    private val id: UUID? = null

    @Column(name = "DOG_NAME", length = 50, nullable = false, unique = false)
    private val name: String? = null

    @Column(name = "DOG_AGE")
    private val age: Int? = null // getters and setters
    // ...
}
