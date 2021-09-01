package net.corda.kryoserialization.resolver

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Registration
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.serializers.FieldSerializer
import net.corda.classinfo.ClassInfoService
import net.corda.kryoserialization.serializers.ThrowableSerializer
import net.corda.sandbox.SandboxGroup
import net.corda.utilities.reflection.kotlinObjectInstance
import net.corda.v5.crypto.BasicHashingService
import java.util.*

/**
 * Corda specific class resolver which enables extra customisation for the purposes of serialization using Kryo
 */
class CordaClassResolver(
    classInfoService: ClassInfoService,
    sandboxGroup: SandboxGroup,
    hashingService: BasicHashingService
) : SandboxClassResolver(classInfoService, sandboxGroup, hashingService) {

    // These classes are assignment-compatible Java equivalents of Kotlin classes.
    // The point is that we do not want to send Kotlin types "over the wire" via RPC.
    private val javaAliases: Map<Class<*>, Class<*>> = mapOf(
        listOf<Any>().javaClass to Collections.emptyList<Any>().javaClass,
        setOf<Any>().javaClass to Collections.emptySet<Any>().javaClass,
        mapOf<Any, Any>().javaClass to Collections.emptyMap<Any, Any>().javaClass
    )

    private fun typeForSerializationOf(type: Class<*>): Class<*> = javaAliases[type] ?: type

    /** Returns the registration for the specified class, or null if the class is not registered.  */
    override fun getRegistration(type: Class<*>): Registration? {
        val targetType = typeForSerializationOf(type)
        return super.getRegistration(targetType)
    }

    override fun registerImplicit(type: Class<*>): Registration {
        val targetType = typeForSerializationOf(type)
        val objectInstance = targetType.kotlinObjectInstance

        // We have to set reference to true, since the flag influences how String fields are treated and we
        // want it to be consistent.
        val references = kryo.references
        try {
            kryo.references = true
            val serializer = when {
                objectInstance != null -> KotlinObjectSerializer(objectInstance)
                kotlin.jvm.internal.Lambda::class.java.isAssignableFrom(targetType) ->
                    // Kotlin lambdas extend this class and any captured variables are stored in synthetic fields
                    FieldSerializer<Any>(kryo, targetType).apply { setIgnoreSyntheticFields(false) }
                Throwable::class.java.isAssignableFrom(targetType) -> ThrowableSerializer(kryo, targetType)
                else -> kryo.getDefaultSerializer(targetType)
            }
            return register(Registration(targetType, serializer, NAME.toInt()))
        } finally {
            kryo.references = references
        }
    }

    override fun writeName(output: Output, type: Class<*>, registration: Registration) {
        super.writeName(output, registration.type ?: type, registration)
    }

    // Trivial Serializer which simply returns the given instance, which we already know is a Kotlin object
    private class KotlinObjectSerializer(private val objectInstance: Any) : Serializer<Any>() {
        override fun read(kryo: Kryo, input: Input, type: Class<Any>): Any = objectInstance
        override fun write(kryo: Kryo, output: Output, obj: Any) = Unit
    }

    // Need to clear out class names from attachments.
    override fun reset() {
        super.reset()
        // Kryo creates a cache of class name to Class<*> which does not work so well with multiple class loaders.
        // TODOs: come up with a more efficient way.  e.g. segregate the name space by class loader.
        if (nameToClass != null) {
            val classesToRemove: MutableList<String> = ArrayList(nameToClass.size)
            nameToClass.entries()
                .forEach { classesToRemove += it.key }
            for (className in classesToRemove) {
                nameToClass.remove(className)
            }
        }
    }
}
