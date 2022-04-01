package net.corda.customserializer.b

import net.corda.v5.serialization.SerializationCustomSerializer

class CustomSerializerB : SerializationCustomSerializer<NeedsCustomSerializerExampleB, CustomSerializerB.MyProxy> {
    override fun fromProxy(proxy: MyProxy): NeedsCustomSerializerExampleB = NeedsCustomSerializerExampleB(proxy.integer)
    override fun toProxy(obj: NeedsCustomSerializerExampleB): MyProxy = MyProxy(obj.b)

    data class MyProxy(val integer: Int)
}
