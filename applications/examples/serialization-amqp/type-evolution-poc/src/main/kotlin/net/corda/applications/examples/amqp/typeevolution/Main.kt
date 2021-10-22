package net.corda.applications.examples.amqp.typeevolution

import net.corda.internal.serialization.AMQP_STORAGE_CONTEXT
import net.corda.internal.serialization.AllWhitelist
import net.corda.internal.serialization.AlwaysAcceptEncodingWhitelist
import net.corda.internal.serialization.SerializationContextImpl
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder
import net.corda.internal.serialization.amqp.amqpMagic
import net.corda.v5.base.types.ByteSequence
import net.corda.v5.serialization.SerializationContext
import net.corda.v5.serialization.annotations.CordaSerializationTransformRename
import net.corda.v5.serialization.annotations.CordaSerializationTransformRenames
import net.corda.v5.serialization.annotations.DeprecatedConstructorForDeserialization

val factory = SerializerFactoryBuilder.build(AllWhitelist)
val output = SerializationOutput(factory)
val input = DeserializationInput(factory)

fun main() {
//    saveResourceFiles()

//    val context = SerializationContextImpl(
//        amqpMagic,
//        AddNullableProperty::class.java.getClassLoader(),
//        AllWhitelist,
//        emptyMap(),
//        true,
//        SerializationContext.UseCase.Storage,
//        null,
//        AlwaysAcceptEncodingWhitelist
//    )

    println("AddNullableProperty = " + (input.deserialize(ByteSequence.of(AddNullableProperty::class.java.getResource("addNullableProperty.bin")!!.readBytes()), AddNullableProperty::class.java, AMQP_STORAGE_CONTEXT) == AddNullableProperty(10, null)))
    println("AddNonNullableProperty = " + (input.deserialize(ByteSequence.of(AddNullableProperty::class.java.getResource("addNonNullableProperty.bin")!!.readBytes()), AddNonNullableProperty::class.java, AMQP_STORAGE_CONTEXT) == AddNonNullableProperty(10, 0)))
    println("MultipleEvolutions = " + (input.deserialize(ByteSequence.of(AddNullableProperty::class.java.getResource("multipleEvolutions.bin")!!.readBytes()), MultipleEvolutions::class.java, AMQP_STORAGE_CONTEXT) == MultipleEvolutions(10, 0, 0)))
    println("MultipleEvolutions = " + (input.deserialize(ByteSequence.of(AddNullableProperty::class.java.getResource("multipleEvolutions-2.bin")!!.readBytes()), MultipleEvolutions::class.java, AMQP_STORAGE_CONTEXT) == MultipleEvolutions(10, 20, 0)))
    println("RemovingProperties = " + (input.deserialize(ByteSequence.of(AddNullableProperty::class.java.getResource("removingProperties.bin")!!.readBytes()), RemovingProperties::class.java, AMQP_STORAGE_CONTEXT) == RemovingProperties(1)))
    println("ReorderConstructorParameters = " + (input.deserialize(ByteSequence.of(AddNullableProperty::class.java.getResource("reorderConstructorParameters.bin")!!.readBytes()), ReorderConstructorParameters::class.java, AMQP_STORAGE_CONTEXT) == ReorderConstructorParameters(2, 1)))
//    println("RenameEnum = " + (input.deserialize(ByteSequence.of(AddNullableProperty::class.java.getResource("renameEnum.bin")!!.readBytes()), RenameEnum::class.java, AMQP_STORAGE_CONTEXT)))
    println("AddEnumValue = " + (input.deserialize(ByteSequence.of(AddNullableProperty::class.java.getResource("addEnumValue.bin")!!.readBytes()), AddEnumValue::class.java, AMQP_STORAGE_CONTEXT) == AddEnumValue.A))
}

// Before
//
//// Add nullable property
//data class AddNullableProperty(val a: Int)
//
//// Add non-nullable property
//data class AddNonNullableProperty(val a: Int)
//
//// Multiple evolutions
//data class MultipleEvolutions(val a: Int)
//data class MultipleEvolutions(val a: Int, val b: Int) {
//    @DeprecatedConstructorForDeserialization(1)
//    constructor(a: Int) : this(a, 0)
//}
//
//// Removing properties
//data class RemovingProperties(val a: Int, val b: Int)
//
//// Reorder constructor parameters
//data class ReorderConstructorParameters(val a: Int, val b: Int)
//
//// Rename enum
//enum class RenameEnum {
//    A, B
//}
//
//// Add enum value
//@CordaSerializationTransformEnumDefaults(CordaSerializationTransformEnumDefault(new = "C", old = "A"))
//enum class AddEnumValue {
//    A, B, C
//}


// After

// Add nullable property
data class AddNullableProperty(val a: Int, val b: Int?)

// Add non-nullable property
data class AddNonNullableProperty(val a: Int, val b: Int) {
    @DeprecatedConstructorForDeserialization(1)
    constructor(a: Int) : this(a, 0)
}

// Multiple evolutions
data class MultipleEvolutions(val a: Int, val b: Int, val c: Int) {
    @DeprecatedConstructorForDeserialization(1)
    constructor(a: Int) : this(a, 0, 0)
    @DeprecatedConstructorForDeserialization(2)
    constructor(a: Int, b: Int) : this(a, b, 0)
}

// Removing properties
data class RemovingProperties(val a: Int)

// Reorder constructor parameters
data class ReorderConstructorParameters(val b: Int, val a: Int)

// Rename enum
@CordaSerializationTransformRenames(CordaSerializationTransformRename(to = "C", from = "B"))
enum class RenameEnum {
    A, C
}

// Add enum value
enum class AddEnumValue {
    A, B
}



// Save resource files
fun saveResourceFiles() {

//    // Step 1
//    val addNullableProperty = AddNullableProperty(10)
//    val addNonNullableProperty = AddNonNullableProperty(10)
//    val multipleEvolutions = MultipleEvolutions(10)
//    val removingProperties = RemovingProperties(1, 2)
//    val reorderConstructorParameters = ReorderConstructorParameters(1, 2)
//    val renameEnum = RenameEnum.B
//    val addEnumValue = AddEnumValue.C
//
//    File("applications/examples/serialization-amqp/type-evolution-poc/src/main/resources/net/corda/applications/examples/amqp/typeevolution/addNullableProperty.bin").writeBytes(output.serialize(addNullableProperty, AMQP_STORAGE_CONTEXT).bytes)
//    File("applications/examples/serialization-amqp/type-evolution-poc/src/main/resources/net/corda/applications/examples/amqp/typeevolution/addNonNullableProperty.bin").writeBytes(output.serialize(addNonNullableProperty, AMQP_STORAGE_CONTEXT).bytes)
//    File("applications/examples/serialization-amqp/type-evolution-poc/src/main/resources/net/corda/applications/examples/amqp/typeevolution/multipleEvolutions.bin").writeBytes(output.serialize(multipleEvolutions, AMQP_STORAGE_CONTEXT).bytes)
//    File("applications/examples/serialization-amqp/type-evolution-poc/src/main/resources/net/corda/applications/examples/amqp/typeevolution/removingProperties.bin").writeBytes(output.serialize(removingProperties, AMQP_STORAGE_CONTEXT).bytes)
//    File("applications/examples/serialization-amqp/type-evolution-poc/src/main/resources/net/corda/applications/examples/amqp/typeevolution/reorderConstructorParameters.bin").writeBytes(output.serialize(reorderConstructorParameters, AMQP_STORAGE_CONTEXT).bytes)
//    File("applications/examples/serialization-amqp/type-evolution-poc/src/main/resources/net/corda/applications/examples/amqp/typeevolution/renameEnum.bin").writeBytes(output.serialize(renameEnum, AMQP_STORAGE_CONTEXT).bytes)
//    File("applications/examples/serialization-amqp/type-evolution-poc/src/main/resources/net/corda/applications/examples/amqp/typeevolution/addEnumValue.bin").writeBytes(output.serialize(addEnumValue, AMQP_STORAGE_CONTEXT).bytes)


//    // Step 2
//    val multipleEvolutions = MultipleEvolutions(10, 20)
//
//    File("applications/examples/serialization-amqp/type-evolution-poc/src/main/resources/net/corda/applications/examples/amqp/typeevolution/multipleEvolutions-2.bin").writeBytes(output.serialize(multipleEvolutions, AMQP_STORAGE_CONTEXT).bytes)
}