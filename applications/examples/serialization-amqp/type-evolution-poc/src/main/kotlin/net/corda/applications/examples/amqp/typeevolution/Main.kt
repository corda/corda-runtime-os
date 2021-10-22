package net.corda.applications.examples.amqp.typeevolution

import net.corda.internal.serialization.AMQP_STORAGE_CONTEXT
import net.corda.internal.serialization.AllWhitelist
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder
import net.corda.v5.serialization.annotations.CordaSerializationTransformEnumDefault
import net.corda.v5.serialization.annotations.CordaSerializationTransformEnumDefaults
import net.corda.v5.serialization.annotations.CordaSerializationTransformRename
import net.corda.v5.serialization.annotations.CordaSerializationTransformRenames
import net.corda.v5.serialization.annotations.DeprecatedConstructorForDeserialization
import java.io.File

val factory = SerializerFactoryBuilder.build(AllWhitelist)
val output = SerializationOutput(factory)
val input = DeserializationInput(factory)

fun main() {
    saveResourceFiles()
    val resource = AddNullableProperty::class.java.getResource("addNullableProperty.bin")
    println(resource)
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




// Save resource files
fun saveResourceFiles() {
    val addNullableProperty = AddNullableProperty(10)
    val addNonNullableProperty = AddNonNullableProperty(10)
    val multipleEvolutions = MultipleEvolutions(10)
    val removingProperties = RemovingProperties(1, 2)
    val reorderConstructorParameters = ReorderConstructorParameters(1, 2)
    val renameEnum = RenameEnum.B
    val addEnumValue = AddEnumValue.C


    File("applications/examples/serialization-amqp/type-evolution-poc/src/main/resources/net/corda/applications/examples/amqp/typeevolution/addNullableProperty.bin").writeBytes(output.serialize(addNullableProperty, AMQP_STORAGE_CONTEXT).bytes)
    File("applications/examples/serialization-amqp/type-evolution-poc/src/main/resources/net/corda/applications/examples/amqp/typeevolution/addNonNullableProperty.bin").writeBytes(output.serialize(addNonNullableProperty, AMQP_STORAGE_CONTEXT).bytes)
    File("applications/examples/serialization-amqp/type-evolution-poc/src/main/resources/net/corda/applications/examples/amqp/typeevolution/multipleEvolutions.bin").writeBytes(output.serialize(multipleEvolutions, AMQP_STORAGE_CONTEXT).bytes)
    File("applications/examples/serialization-amqp/type-evolution-poc/src/main/resources/net/corda/applications/examples/amqp/typeevolution/removingProperties.bin").writeBytes(output.serialize(removingProperties, AMQP_STORAGE_CONTEXT).bytes)
    File("applications/examples/serialization-amqp/type-evolution-poc/src/main/resources/net/corda/applications/examples/amqp/typeevolution/reorderConstructorParameters.bin").writeBytes(output.serialize(reorderConstructorParameters, AMQP_STORAGE_CONTEXT).bytes)
    File("applications/examples/serialization-amqp/type-evolution-poc/src/main/resources/net/corda/applications/examples/amqp/typeevolution/renameEnum.bin").writeBytes(output.serialize(renameEnum, AMQP_STORAGE_CONTEXT).bytes)
    File("applications/examples/serialization-amqp/type-evolution-poc/src/main/resources/net/corda/applications/examples/amqp/typeevolution/addEnumValue.bin").writeBytes(output.serialize(addEnumValue, AMQP_STORAGE_CONTEXT).bytes)
}