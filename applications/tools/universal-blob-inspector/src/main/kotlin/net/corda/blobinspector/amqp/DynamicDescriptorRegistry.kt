package net.corda.blobinspector.amqp

import org.apache.qpid.proton.amqp.UnsignedInteger

class DynamicDescriptorRegistry(private val lenientBuiltIns: Boolean = false) {
    private val prefix = "net.corda:"

    private val types: MutableMap<Descriptor, TypeHandle> = mutableMapOf()

    init {
        for (value in PredefinedDescriptorRegistry.values()) {
            types[Descriptor(null, value.amqpDescriptor)] = PredefinedTypeHandle(value.name)
        }
    }

    fun register(type: TypeNotation) {
        types[type.descriptor] = when (type) {
            is CompositeType -> CompositeTypeHandle(type)
            is RestrictedType -> RestrictedTypeHandle(type)
        }
    }

    operator fun get(descriptor: Descriptor): TypeHandle? {
        return types[descriptor] ?: resolveBuiltIn(descriptor)
    }

    private fun resolveBuiltIn(descriptor: Descriptor): TypeHandle? {
        if (descriptor.name == null || (!lenientBuiltIns && !descriptor.name.startsWith(prefix))) return null
        val name = descriptor.name.substring(prefix.length)
        return try {
            if (!lenientBuiltIns) Class.forName(name)
            val handle = CustomTypeHandle(name)
            types[Descriptor(name)] = handle
            handle
        } catch (e: ClassNotFoundException) {
            null
        }
    }

    interface TypeHandle {
        fun transform(value: Any?, referencedObjects: MutableList<Any?>): Any?
    }

    class CompositeTypeHandle(private val type: CompositeType) : TypeHandle {
        override fun transform(value: Any?, referencedObjects: MutableList<Any?>): Any {
            val list = value as List<Any?>
            val map: MutableMap<String, Any?> = mutableMapOf()
            map["_class"] = type.name
            type.fields.forEachIndexed { index, field ->
                map[field.name] = list[index]
            }
            return map
        }
    }

    class RestrictedTypeHandle(
        @Suppress("Unused")
        val type: RestrictedType
    ) : TypeHandle {
        override fun transform(value: Any?, referencedObjects: MutableList<Any?>): Any? {
            return value
        }
    }

    class CustomTypeHandle(private val name: String) : TypeHandle {
        override fun transform(value: Any?, referencedObjects: MutableList<Any?>): Any {
            return mapOf("_custom" to name, "_value" to value)
        }
    }

    class PredefinedTypeHandle(private val name: String) : TypeHandle {
        override fun transform(value: Any?, referencedObjects: MutableList<Any?>): Any? {
            return if (name == "REFERENCED_OBJECT") {
                val position = (value as UnsignedInteger).toInt()
                // println(position)
                // referencedObjects.forEachIndexed { index, obj -> println("$index $obj") }
                referencedObjects[position]
            } else {
                mapOf("_predefined" to name, "_value" to value)
            }
        }
    }
}
