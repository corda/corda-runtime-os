package net.corda.internal.serialization.amqp

import org.apache.qpid.proton.amqp.Symbol
import java.lang.reflect.Type

/**
 * Additional base features for a custom serializer for a particular class [withInheritance] is false
 * or super class / interfaces [withInheritance] is true
 */
abstract class CustomSerializerImp<T : Any>(protected val clazz: Class<T>, protected val withInheritance: Boolean) :
    CustomSerializer<T>() {
    override val type: Type get() = clazz
    override val typeDescriptor: Symbol = typeDescriptorFor(clazz)
    override fun writeClassInfo(output: SerializationOutput) {}
    override val descriptor: Descriptor = Descriptor(typeDescriptor)
    override fun isSerializerFor(clazz: Class<*>): Boolean =
        if (withInheritance) this.clazz.isAssignableFrom(clazz) else this.clazz == clazz
}