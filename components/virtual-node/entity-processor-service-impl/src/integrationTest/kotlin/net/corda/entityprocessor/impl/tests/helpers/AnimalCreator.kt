package net.corda.entityprocessor.impl.tests.helpers

import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.createCat
import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.createDog
import net.corda.sandboxgroupcontext.SandboxGroupContext
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

    private val colours = listOf("Black and white", "Tabby", "White", "Black", "Tortoiseshell")


    /** Create some dogs */
    fun createDogs(sandbox: SandboxGroupContext, times: Int=1): List<SandboxHelper.Box> = dogNames.map {
            (1..times).map { i -> sandbox.createDog( "${it.first} $i", owner=it.second) }
        }.flatten()

    /** Persist some cats.  Returns the total persisted. */
    fun createCats(sandbox: SandboxGroupContext, times: Int): List<SandboxHelper.Box> = catNames.map { name ->
            (1..times).map {i -> sandbox.createCat("${name.first} $i", colour=colours.random(), ownerName=name.second, ownerAge = (20..30).random()) }
        }.flatten()
}
