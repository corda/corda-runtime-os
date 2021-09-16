package net.corda.kryoserialization.resolver

import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.Registration
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.util.DefaultClassResolver
import com.esotericsoftware.kryo.util.IntMap
import net.corda.kryoserialization.serializers.KotlinObjectSerializer
import net.corda.sandbox.SandboxGroup
import net.corda.utilities.reflection.kotlinObjectInstance
import java.util.*

/**
 * Corda specific class resolver which enables extra customisation for the purposes of serialization using Kryo
 */
open class CordaClassResolver(
    private val sandboxGroup: SandboxGroup
) : DefaultClassResolver() {

    private val noCpkId = -1

    override fun registerImplicit(type: Class<*>): Registration {
        val objectInstance = type.kotlinObjectInstance

        // We have to set reference to true, since the flag influences how String fields are treated and we
        // want it to be consistent.
        val references = kryo.references
        try {
            kryo.references = true
            val serializer = when {
                objectInstance != null -> KotlinObjectSerializer(objectInstance)
                else -> kryo.getDefaultSerializer(type)
            }
            return register(Registration(type, serializer, NAME.toInt()))
        } finally {
            kryo.references = references
        }
    }

    /**
     * Overwrites internal [writeName] function to allow for capturing CPK metadata
     * in addition to the [type] class name.
     *
     * @param [output] an output stream for writing the serialised data.
     * @param [type] the type to be serialised.
     * @param [registration] a registry of serializers.
     */
    override fun writeName(output: Output, type: Class<*>, registration: Registration) {
        super.writeName(output, registration.type ?: type, registration)
        output.writeString(sandboxGroup.getStaticTag(type))
    }

    /**
     * Overwrites internal [readName] function to allow for identifying CPK metadata
     * associated with the [type] class name.
     *
     * @param [input] an input stream for reading the serialised data.
     *
     * @return [Registration] a registry of serializers.
     */
    override fun readName(input: Input): Registration {
        val nameId = input.readVarInt(true)
        if (nameIdToClass == null) nameIdToClass = IntMap<Class<*>>()
        val type = if (nameIdToClass.containsKey(nameId)) {
            nameIdToClass[nameId]
        } else {
            // Only read the class name the first time encountered in object graph.
            val className = input.readString()
            val cpkId = input.readString()
            if (cpkId == noCpkId.toString()) {
                try {
                    Class.forName(className, false, kryo.classLoader)
                } catch (ex: ClassNotFoundException) {
                    throw KryoException("Unable to find class: $className in default classloader (not in a Sandbox).")
                }
            } else {
                sandboxGroup.getClass(className, cpkId).also {
                    nameIdToClass.put(nameId, it)
                }
            }
        }
        return kryo.getRegistration(type)
    }
}
