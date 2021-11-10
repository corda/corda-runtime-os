package net.corda.applications.examples.amqp.customserializer.exampleb

class NeedsCustomSerializerExampleB(a: Int) {
    val b: Int = a
    override fun toString(): String = "NeedsCustomSerializerExampleB(b=$b)"
}