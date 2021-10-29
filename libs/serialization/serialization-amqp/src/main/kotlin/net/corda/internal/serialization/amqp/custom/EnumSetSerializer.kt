package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.SerializationContext
import net.corda.internal.serialization.amqp.CustomSerializer
import net.corda.internal.serialization.amqp.MapSerializer
import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.v5.base.util.uncheckedCast
import java.util.EnumSet

/**
 * A serializer that writes out an [EnumSet] as a type, plus list of instances in the set.
 */
class EnumSetSerializer(
    factory: SerializerFactory
) : CustomSerializer.Proxy<EnumSet<*>, EnumSetSerializer.EnumSetProxy>(
    EnumSet::class.java,
    EnumSetProxy::class.java,
    factory,
    withInheritance = true
) {
    override val additionalSerializers: Iterable<CustomSerializer<out Any>> = listOf(ClassSerializer(factory))

    override fun toProxy(obj: EnumSet<*>, context: SerializationContext): EnumSetProxy
        = EnumSetProxy(elementType(obj), obj.toList())

    private fun elementType(set: EnumSet<*>): Class<*> {
        return if (set.isEmpty()) {
            EnumSet.complementOf(set).first().javaClass
        } else {
            set.first().javaClass
        }
    }

    override fun fromProxy(proxy: EnumSetProxy): EnumSet<*> {
        return if (proxy.elements.isEmpty()) {
            EnumSet.noneOf(uncheckedCast<Class<*>, Class<MapSerializer.EnumJustUsedForCasting>>(proxy.clazz))
        } else {
            EnumSet.copyOf(uncheckedCast<List<Any>, List<MapSerializer.EnumJustUsedForCasting>>(proxy.elements))
        }
    }

    data class EnumSetProxy(val clazz: Class<*>, val elements: List<Any>)
}
