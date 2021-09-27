package net.corda.kryoserialization.resolver

import com.esotericsoftware.kryo.Registration
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.util.DefaultClassResolver
import com.esotericsoftware.kryo.util.IdentityObjectIntMap
import com.esotericsoftware.kryo.util.IntMap
import net.corda.kryoserialization.serializers.KotlinObjectSerializer
import net.corda.sandbox.SandboxGroup
import net.corda.utilities.reflection.kotlinObjectInstance

/**
 * Corda specific class resolver which enables extra customisation for the purposes of serialization using Kryo
 */
class CordaClassResolver(
    private val sandboxGroup: SandboxGroup
) : DefaultClassResolver() {

    override fun registerImplicit(type: Class<*>): Registration {
        val objectInstance = type.kotlinObjectInstance

        // We have to set reference to true, since the flag influences how String fields are treated,
        // and we want it to be consistent.
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
        output.writeVarInt(NAME + 2, true)
        if (classToNameId == null) classToNameId = IdentityObjectIntMap<Class<*>>()
        var nameId = classToNameId[type, -1]
        if (nameId != -1) {
            output.writeVarInt(nameId, true)
            return
        }
        // Only write the class name the first time encountered in object graph.
        nameId = nextNameId++
        classToNameId.put(type, nameId)
        output.writeVarInt(nameId, true)
        output.writeString(type.name)
        output.writeString(sandboxGroup.getStaticTag(type))
    }

    /**
     * Overwrites internal [readName] function to allow for identifying CPK metadata
     * associated with the class name.
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
            val classTag = input.readString()
            sandboxGroup.getClass(className, classTag).also {
                nameIdToClass.put(nameId, it)
            }
        }
        return kryo.getRegistration(type)
    }
}
