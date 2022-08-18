package net.corda.entityprocessor.impl.tests.helpers

import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.createCatInstance
import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.createDogInstance
import java.time.Instant
import java.util.UUID

object AnimalCreator {
    private val dogNames = listOf(
        "Rover" to "Mr Smith",
        "Butch" to "Mr Jones",
        "Snoopy" to "Charlie Brown",
        "Gromit" to "Wallace",
        "Toto" to "Dorothy",
        "Lassie" to null,
        "Nipper" to "HMV",
        "Eddie" to "Martin Crane"
    )

    // Does anyone really *own* a cat?
    private val catNames = listOf(
        "Mr Bigglesworth" to "Dr Evil",
        "The Cat" to "Dave Lister",
        "Tom" to "Not Jerry, or Joel",
        "Schrodinger's Cat" to "Maybe Maybe Not"
    )

    /** Persist some dogs.  Returns the total persisted. */
    fun persistDogs(ctx: DbTestContext, times: Int): Int {
        dogNames.forEach {
            for (i in 1..times) {
                val name = "${it.first} $i"
                val dog = ctx.sandbox.createDogInstance(UUID.randomUUID(), name, Instant.now(), it.second)
                ctx.persist(dog)
            }
        }

        return dogNames.size * times
    }

    /** Persist some cats.  Returns the total persisted. */
    fun persistCats(ctx: DbTestContext, times: Int): Int {
        val colours = listOf("Black and white", "Tabby", "White", "Black", "Tortoiseshell")
        catNames.forEach {
            for (i in 1..times) {
                val name = "${it.first} $i"
                val obj = ctx.sandbox.createCatInstance(UUID.randomUUID(), name, colours.random(), UUID.randomUUID(), it.second, (20..30).random())
                ctx.persist(obj)
            }
        }

        return catNames.size * times
    }
}
