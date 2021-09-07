package net.corda.kryoserialization.resolver

import com.esotericsoftware.kryo.Registration
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.serializers.FieldSerializer
import net.corda.classinfo.ClassInfoService
import net.corda.kryoserialization.serializers.KotlinObjectSerializer
import net.corda.kryoserialization.serializers.ThrowableSerializer
import net.corda.sandbox.SandboxGroup
import net.corda.utilities.reflection.kotlinObjectInstance
import net.corda.v5.crypto.BasicHashingService

/**
 * Corda specific class resolver which enables extra customisation for the purposes of serialization using Kryo
 */
class CordaClassResolver(
    classInfoService: ClassInfoService,
    sandboxGroup: SandboxGroup,
    hashingService: BasicHashingService
) : SandboxClassResolver(classInfoService, sandboxGroup, hashingService) {

    /** Returns the registration for the specified class, or null if the class is not registered.  */
    override fun getRegistration(type: Class<*>): Registration? {
        return super.getRegistration(type)
    }

    override fun registerImplicit(type: Class<*>): Registration {
        val objectInstance = type.kotlinObjectInstance

        // We have to set reference to true, since the flag influences how String fields are treated and we
        // want it to be consistent.
        val references = kryo.references
        try {
            kryo.references = true
            val serializer = when {
                objectInstance != null -> KotlinObjectSerializer(objectInstance)
                kotlin.jvm.internal.Lambda::class.java.isAssignableFrom(type) ->
                    // Kotlin lambdas extend this class and any captured variables are stored in synthetic fields
                    FieldSerializer<Any>(kryo, type).apply { setIgnoreSyntheticFields(false) }
                Throwable::class.java.isAssignableFrom(type) -> ThrowableSerializer(kryo, type)
                else -> kryo.getDefaultSerializer(type)
            }
            return register(Registration(type, serializer, NAME.toInt()))
        } finally {
            kryo.references = references
        }
    }

    override fun writeName(output: Output, type: Class<*>, registration: Registration) {
        super.writeName(output, registration.type ?: type, registration)
    }
}
