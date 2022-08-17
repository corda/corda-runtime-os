package net.corda.entityprocessor.impl.tests.helpers

import net.corda.entityprocessor.impl.internal.EntitySandboxContextTypes
import net.corda.sandbox.SandboxGroup
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.v5.application.serialization.SerializationService
import java.time.Instant
import java.util.UUID

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
            ?: throw ClassNotFoundException("$DOG_CLASS_NAME not found")
    }

    fun SandboxGroup.getCatKeyClass(): Class<*> {
        return this.loadClassFromMainBundles(CAT_KEY_CLASS_NAME)
            ?: throw ClassNotFoundException("$CAT_KEY_CLASS_NAME not found")
    }

    fun SandboxGroup.getCatClass(): Class<*> {
        return this.loadClassFromMainBundles(CAT_CLASS_NAME)
            ?: throw ClassNotFoundException("$CAT_CLASS_NAME not found")
    }

    fun SandboxGroup.getOwnerClass(): Class<*> {
        return this.loadClassFromMainBundles("net.corda.testing.bundles.cats.Owner")
            ?: throw ClassNotFoundException("net.corda.testing.bundles.cats.Owner not found")
    }

    fun SandboxGroupContext.createDogInstance(id: UUID, name: String, date: Instant, owner: String?): Any {
        val dogCtor = this.sandboxGroup.getDogClass().getDeclaredConstructor(UUID::class.java, String::class.java, Instant::class.java, String::class.java)
        return dogCtor.newInstance(id, name, date, owner)
    }

    fun SandboxGroupContext.createCatKeyInstance(id: UUID, name: String): Any {
        val keyCtor = this.sandboxGroup.getCatKeyClass().getDeclaredConstructor(UUID::class.java, String::class.java)
        return keyCtor.newInstance(id, name)
    }

    fun SandboxGroupContext.createCatInstance(id: UUID, name: String, colour: String, ownerId: UUID, ownerName: String, ownerAge: Int): Any {
        val ownerClass = this.sandboxGroup.getOwnerClass()
        val ownerCtor = ownerClass.getDeclaredConstructor(UUID::class.java, String::class.java, Int::class.java)
        val catCtor = this.sandboxGroup.getCatClass().getDeclaredConstructor(UUID::class.java, String::class.java, String::class.java, ownerClass)
        val owner = ownerCtor.newInstance(ownerId, ownerName, ownerAge)
        return catCtor.newInstance(id, name, colour, owner)
    }

    fun SandboxGroupContext.getSerializer(): SerializationService {
        return this.getObjectByKey(EntitySandboxContextTypes.SANDBOX_SERIALIZER)!!
    }
}
