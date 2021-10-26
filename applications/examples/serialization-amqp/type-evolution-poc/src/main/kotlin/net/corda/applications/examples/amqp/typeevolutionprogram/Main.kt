package net.corda.applications.examples.amqp.typeevolutionprogram

import net.corda.internal.serialization.AMQP_STORAGE_CONTEXT
import net.corda.internal.serialization.AllWhitelist
import net.corda.internal.serialization.AlwaysAcceptEncodingWhitelist
import net.corda.internal.serialization.SerializationContextImpl
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder
import net.corda.internal.serialization.amqp.amqpMagic
import net.corda.osgi.api.Application
import net.corda.v5.base.types.ByteSequence
import net.corda.v5.base.util.contextLogger
import net.corda.v5.serialization.SerializationContext
import net.corda.v5.serialization.annotations.CordaSerializationTransformRename
import net.corda.v5.serialization.annotations.CordaSerializationTransformRenames
import net.corda.v5.serialization.annotations.DeprecatedConstructorForDeserialization
import org.osgi.framework.BundleContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger

val factory = SerializerFactoryBuilder.build(AllWhitelist)
val output = SerializationOutput(factory)
val input = DeserializationInput(factory)


//
//net.corda.osgi.api Application.kt public interface Application : AutoCloseable
//The osgi-framework-bootstrap module calls startup of the class implementing this interface as entry point of the application and shutdown before to stop the OSGi framework.
//NOTE: To distribute an application as a bootable JAR built with the corda.common.app plugin, only one class must implement this interface because that class is the entry point of the application.
//The class implementing this interface must define an OSGi component and register as an OSGi service.
//EXAMPLE
//The code shows for to implement the Application interface and inject in the constructor a OSGi service/component annotated the @Reference.
//See the README.md file of the buildSrc module for the common-appplugin for more infos.
//import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
//import net.corda.osgi.api.Application
//import org.osgi.service.component.annotations.Activate
//import org.osgi.service.component.annotations.Component
//import org.osgi.service.component.annotations.Reference
//
//@Component(immediate = true)
//class App @Activate constructor(
//    @Reference(service = KafkaTopicAdmin::class)
//    private var kafkaTopicAdmin: KafkaTopicAdmin,
//): Application {
//
//    override fun startup(args: Array<String>) {
//        println("startup with ${kafkaTopicAdmin}")
//    }
//
//    override fun shutdown() {
//        println("shutdown")
//    }
//}

@Component(immediate = true)
class Main : Application {
    private companion object {
        private val logger: Logger = contextLogger()
    }

    init {
        logger.info("INIT")
    }

    override fun startup(args: Array<String>) {
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

        logger.info("AddNullableProperty = " + (input.deserialize(ByteSequence.of(AddNullableProperty::class.java.getResource("addNullableProperty.bin")!!.readBytes()), AddNullableProperty::class.java, AMQP_STORAGE_CONTEXT) == AddNullableProperty(10, null)))
        logger.info("AddNonNullableProperty = " + (input.deserialize(ByteSequence.of(AddNullableProperty::class.java.getResource("addNonNullableProperty.bin")!!.readBytes()), AddNonNullableProperty::class.java, AMQP_STORAGE_CONTEXT) == AddNonNullableProperty(10, 0)))
        logger.info("MultipleEvolutions = " + (input.deserialize(ByteSequence.of(AddNullableProperty::class.java.getResource("multipleEvolutions.bin")!!.readBytes()), MultipleEvolutions::class.java, AMQP_STORAGE_CONTEXT) == MultipleEvolutions(10, 0, 0)))
        logger.info("MultipleEvolutions = " + (input.deserialize(ByteSequence.of(AddNullableProperty::class.java.getResource("multipleEvolutions-2.bin")!!.readBytes()), MultipleEvolutions::class.java, AMQP_STORAGE_CONTEXT) == MultipleEvolutions(10, 20, 0)))
        logger.info("RemovingProperties = " + (input.deserialize(ByteSequence.of(AddNullableProperty::class.java.getResource("removingProperties.bin")!!.readBytes()), RemovingProperties::class.java, AMQP_STORAGE_CONTEXT) == RemovingProperties(1)))
        logger.info("ReorderConstructorParameters = " + (input.deserialize(ByteSequence.of(AddNullableProperty::class.java.getResource("reorderConstructorParameters.bin")!!.readBytes()), ReorderConstructorParameters::class.java, AMQP_STORAGE_CONTEXT) == ReorderConstructorParameters(2, 1)))
//    logger.info("RenameEnum = " + (input.deserialize(ByteSequence.of(AddNullableProperty::class.java.getResource("renameEnum.bin")!!.readBytes()), RenameEnum::class.java, AMQP_STORAGE_CONTEXT)))
        logger.info("AddEnumValue = " + (input.deserialize(ByteSequence.of(AddNullableProperty::class.java.getResource("addEnumValue.bin")!!.readBytes()), AddEnumValue::class.java, AMQP_STORAGE_CONTEXT) == AddEnumValue.A))
    }

    override fun shutdown() = Unit
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