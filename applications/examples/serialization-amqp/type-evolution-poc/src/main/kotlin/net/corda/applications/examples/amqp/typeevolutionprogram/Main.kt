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

    }

    override fun shutdown() = Unit
}





