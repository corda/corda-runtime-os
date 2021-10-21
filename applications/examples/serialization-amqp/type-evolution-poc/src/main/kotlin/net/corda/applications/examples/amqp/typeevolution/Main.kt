package net.corda.applications.examples.amqp.typeevolution

import net.corda.internal.serialization.AllWhitelist
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder
import net.corda.v5.serialization.annotations.CordaSerializationTransformEnumDefault
import net.corda.v5.serialization.annotations.CordaSerializationTransformEnumDefaults
import net.corda.v5.serialization.annotations.CordaSerializationTransformRename
import net.corda.v5.serialization.annotations.CordaSerializationTransformRenames
import net.corda.v5.serialization.annotations.DeprecatedConstructorForDeserialization

val factory = SerializerFactoryBuilder.build(AllWhitelist)
val output = SerializationOutput(factory)
val input = DeserializationInput(factory)

fun main() {

}

// Add nullable property
data class AddNullableProperty(val a: Int)
//data class AddNullableProperty(val a: Int, val b: Int?)

// Add non-nullable property
data class AddNonNullableProperty(val a: Int)
//data class AddNonNullableProperty(val a: Int, val b: Int) {
//    @DeprecatedConstructorForDeserialization(1)
//    constructor(a: Int) : this(a, 0)
//}

// Multiple evolutions
data class MultipleEvolutions(val a: Int)
//data class MultipleEvolutions(val a: Int, val b: Int) {
//    @DeprecatedConstructorForDeserialization(1)
//    constructor(a: Int) : this(a, 0)
//}
//data class MultipleEvolutions(val a: Int, val b: Int, val c: Int) {
//    @DeprecatedConstructorForDeserialization(1)
//    constructor(a: Int) : this(a, 0, 0)
//    @DeprecatedConstructorForDeserialization(2)
//    constructor(a: Int, b: Int) : this(a, b, 0)
//}

// Removing properties
data class RemovingProperties(val a: Int, val b: Int)
//data class RemovingProperties(val a: Int)

// Reorder constructor parameters
data class ReorderConstructorParameters(val a: Int, val b: Int)
//data class ReorderConstructorParameters(val b: Int, val a: Int)

// Rename enum
enum class RenameEnum {
    A, B
}
//@CordaSerializationTransformRenames(CordaSerializationTransformRename(to = "C", from = "B"))
//enum class RenameEnum {
//    A, C
//}

// Add enum value
@CordaSerializationTransformEnumDefaults(CordaSerializationTransformEnumDefault(new = "C", old = "A"))
enum class AddEnumValue {
    A, B, C
}
//enum class AddEnumValue {
//    A, B
//}
