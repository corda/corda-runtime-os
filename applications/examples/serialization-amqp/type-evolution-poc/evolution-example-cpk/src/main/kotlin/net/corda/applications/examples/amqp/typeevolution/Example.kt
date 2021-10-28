package net.corda.applications.examples.amqp.typeevolution

import net.corda.internal.serialization.AMQP_STORAGE_CONTEXT
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.v5.base.types.ByteSequence
import net.corda.v5.base.util.contextLogger

class Example {

    companion object {
        val logger = contextLogger()
    }

    fun runExample(input: DeserializationInput){
        logger.info("AddNullableProperty = " + (input.deserialize(ByteSequence.of(AddNullableProperty::class.java.getResource("addNullableProperty.bin")!!.readBytes()), AddNullableProperty::class.java, AMQP_STORAGE_CONTEXT) == AddNullableProperty(10, null)))
        logger.info("AddNonNullableProperty = " + (input.deserialize(ByteSequence.of(AddNullableProperty::class.java.getResource("addNonNullableProperty.bin")!!.readBytes()), AddNonNullableProperty::class.java, AMQP_STORAGE_CONTEXT) == AddNonNullableProperty(10, 0)))
        logger.info("MultipleEvolutions = " + (input.deserialize(ByteSequence.of(AddNullableProperty::class.java.getResource("multipleEvolutions.bin")!!.readBytes()), MultipleEvolutions::class.java, AMQP_STORAGE_CONTEXT) == MultipleEvolutions(10, 0, 0)))
        logger.info("MultipleEvolutions = " + (input.deserialize(ByteSequence.of(AddNullableProperty::class.java.getResource("multipleEvolutions-2.bin")!!.readBytes()), MultipleEvolutions::class.java, AMQP_STORAGE_CONTEXT) == MultipleEvolutions(10, 20, 0)))
        logger.info("RemovingProperties = " + (input.deserialize(ByteSequence.of(AddNullableProperty::class.java.getResource("removingProperties.bin")!!.readBytes()), RemovingProperties::class.java, AMQP_STORAGE_CONTEXT) == RemovingProperties(1)))
        logger.info("ReorderConstructorParameters = " + (input.deserialize(ByteSequence.of(AddNullableProperty::class.java.getResource("reorderConstructorParameters.bin")!!.readBytes()), ReorderConstructorParameters::class.java, AMQP_STORAGE_CONTEXT) == ReorderConstructorParameters(2, 1)))
//    logger.info("RenameEnum = " + (input.deserialize(ByteSequence.of(AddNullableProperty::class.java.getResource("renameEnum.bin")!!.readBytes()), RenameEnum::class.java, AMQP_STORAGE_CONTEXT)))
        logger.info("AddEnumValue = " + (input.deserialize(ByteSequence.of(AddNullableProperty::class.java.getResource("addEnumValue.bin")!!.readBytes()), AddEnumValue::class.java, AMQP_STORAGE_CONTEXT) == AddEnumValue.A))
    }


    // Save resource files
    fun saveResourceFiles(output: SerializationOutput) {

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
}