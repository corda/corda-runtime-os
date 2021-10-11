package net.corda.applications.examples.amqp.customserializer

class NeedsCustomSerializerExampleB(a: Int) {
    val b: Int = a
    override fun toString(): String = "NeedsCustomSerializer(b=$b)"
}