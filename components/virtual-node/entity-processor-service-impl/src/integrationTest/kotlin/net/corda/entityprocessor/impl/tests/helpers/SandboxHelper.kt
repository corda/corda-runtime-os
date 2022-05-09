package net.corda.entityprocessor.impl.tests.helpers

import net.corda.entityprocessor.impl.internal.EntitySandboxContextTypes
import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.createSerializedDog
import net.corda.sandbox.SandboxGroup
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.v5.application.serialization.SerializationService
import java.time.Instant
import java.util.UUID

object SandboxHelper {
    fun SandboxGroup.createDogClass(): Class<*> {
        return this.loadClassFromMainBundles("net.corda.testing.bundles.dogs.Dog")
    }

    fun SandboxGroupContext.createDogInstance(id: UUID, name: String, date: Instant, owner: String): Any {
        val dogCtor = this.sandboxGroup.createDogClass().getDeclaredConstructor(UUID::class.java, String::class.java, Instant::class.java, String::class.java)
        return dogCtor.newInstance(id, name, date, owner)
    }

    fun SandboxGroupContext.getSerializer(): SerializationService {
        return this.getObjectByKey(EntitySandboxContextTypes.SANDBOX_SERIALIZER)!!
    }

    fun SandboxGroupContext.createSerializedDog(id: UUID, name: String, date: Instant, owner: String): ByteArray {
        val dog = this.createDogInstance(id, name, date, owner)
        return this.getSerializer()
            .serialize(dog).bytes
    }
}