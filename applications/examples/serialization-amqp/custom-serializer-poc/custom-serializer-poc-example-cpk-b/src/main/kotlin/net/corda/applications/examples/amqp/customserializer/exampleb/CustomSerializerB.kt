package net.corda.applications.examples.amqp.customserializer.exampleb

import net.corda.v5.serialization.SerializationCustomSerializer

class CustomSerializerB : SerializationCustomSerializer<NeedsCustomSerializerExampleB, Int> {
    override fun fromProxy(proxy: Int): NeedsCustomSerializerExampleB = NeedsCustomSerializerExampleB(proxy)
    override fun toProxy(obj: NeedsCustomSerializerExampleB): Int = obj.b
}