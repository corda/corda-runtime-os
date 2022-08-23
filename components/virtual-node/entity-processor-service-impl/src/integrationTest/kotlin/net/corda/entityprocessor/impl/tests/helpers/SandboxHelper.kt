package net.corda.entityprocessor.impl.tests.helpers

import net.corda.entityprocessor.impl.internal.EntitySandboxContextTypes
import net.corda.sandbox.SandboxGroup
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.v5.application.serialization.SerializationService
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

/**
 * We need to use this object because we cannot include the `Cat` and `Dog` bundles as
 * _direct gradle dependencies_ of this test.  That means they would be loaded by OSGi
 * twice, with a separate class loader.
 */
object SandboxHelper {
    const val DOG_CLASS_NAME = "net.corda.testing.bundles.dogs.Dog"
    const val CAT_CLASS_NAME = "net.corda.testing.bundles.cats.Cat"
    const val CAT_KEY_CLASS_NAME = "net.corda.testing.bundles.cats.CatKey"


    fun SandboxGroup.getDogClass(): Class<*> {
        return this.loadClassFromMainBundles(DOG_CLASS_NAME)
    }

    fun SandboxGroup.getCatKeyClass(): Class<*> {
        return this.loadClassFromMainBundles(CAT_KEY_CLASS_NAME)
    }

    fun SandboxGroup.getCatClass(): Class<*> {
        return this.loadClassFromMainBundles(CAT_CLASS_NAME)
    }

    fun SandboxGroup.getOwnerClass(): Class<*> {
        return this.loadClassFromMainBundles("net.corda.testing.bundles.cats.Owner")
    }

    data class Box(val instance: Any, val id: UUID)

    fun SandboxGroupContext.createDog(name: String="Lassie",  id:UUID = UUID.randomUUID(), date: Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS),owner: String? = "me"): Box {
        val dogCtor = this.sandboxGroup.getDogClass()
            .getDeclaredConstructor(UUID::class.java, String::class.java, Instant::class.java, String::class.java)
        val instance = dogCtor.newInstance(id, name, date, owner)
        return Box(instance, id)
    }

    fun SandboxGroupContext.createCatKeyInstance(id: UUID, name: String): Any {
        val keyCtor = this.sandboxGroup.getCatKeyClass().getDeclaredConstructor(UUID::class.java, String::class.java)
        return keyCtor.newInstance(id, name)
    }

    fun SandboxGroupContext.createCat(
        name: String = "Garfield",
        id: UUID = UUID.randomUUID(),
        colour: String = "Ginger",
        ownerId: UUID = UUID.randomUUID(),
        ownerName: String = "Jim Davies",
        ownerAge: Int = Calendar.getInstance().get(
            Calendar.YEAR
        ) - 1976
    ): Box {
        val ownerClass = this.sandboxGroup.getOwnerClass()
        val ownerCtor = ownerClass.getDeclaredConstructor(UUID::class.java, String::class.java, Int::class.java)
        val catCtor = this.sandboxGroup.getCatClass()
            .getDeclaredConstructor(UUID::class.java, String::class.java, String::class.java, ownerClass)
        val owner = ownerCtor.newInstance(ownerId, ownerName, ownerAge)
        return Box(catCtor.newInstance(id, name, colour, owner), id)
    }

    fun SandboxGroupContext.getSerializer(): SerializationService {
        return this.getObjectByKey(EntitySandboxContextTypes.SANDBOX_SERIALIZER)!!
    }
}
