package net.corda.customserializer.b

class NeedsCustomSerializerExampleB(a: Int) {
    val b: Int = a
    override fun toString(): String = "NeedsCustomSerializerExampleB(b=$b)"
}