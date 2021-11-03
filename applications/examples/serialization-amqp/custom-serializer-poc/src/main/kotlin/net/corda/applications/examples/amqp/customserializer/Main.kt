package net.corda.applications.examples.amqp.customserializer

import net.corda.install.InstallService
import net.corda.internal.serialization.AMQP_STORAGE_CONTEXT
import net.corda.internal.serialization.AllWhitelist
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.DuplicateCustomSerializerException
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.packaging.CPI
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxGroup
import net.corda.v5.serialization.SerializationCustomSerializer
import org.osgi.framework.FrameworkUtil
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.Hashtable


// Requirements:
// - Serialisation can support different serializers per sandbox group
//   - CorDapp provided
//   - Platform provided
// - Platform takes priority over CorDapp provided
// - Log message warning written if CorDapp attempts to replace platform serializer
// - Register the list of internal serializers for use by the sandbox
// - Register the list of CorDapp custom serializers for use by the sandbox


@Component
class Main @Activate constructor(
    @Reference
    private val shutDownService: Shutdown,
    @Reference
    private val sandboxCreationService: SandboxCreationService,
    @Reference
    private val configurationAdmin: ConfigurationAdmin,
    @Reference
    private val installService: InstallService,
) : Application {
    private companion object {
        private val consoleLogger: Logger = LoggerFactory.getLogger("Console")
    }

    private val bundle = FrameworkUtil.getBundle(this::class.java)
    private val bundleContext = bundle.bundleContext

    private fun exit() = shutDownService.shutdown(bundle)

    private val factory = SerializerFactoryBuilder.build(AllWhitelist)

    override fun startup(args: Array<String>) {

        val tmpDir = args[0]
        // Prepare a sandbox group
        configureSystem(tmpDir)

        val cpkA = Path.of(args[1])
        val cpkB = Path.of(args[2])
        val cpkFiles = listOf(cpkA, cpkB)
        val sandbox = prepareSandbox(cpkFiles)

        differentSerializersPerSandboxGroup()
        println("------------------------------------------------")
        platformTakesPriority()
        println("------------------------------------------------")
        logMessageIfAttemptToReplacePlatform()
        println("------------------------------------------------")
        registerInternalSerializers()
        println("------------------------------------------------")
        registerCorDappSerializers()
        println("------------------------------------------------")

//
//        // Prepare a sandbox group
//        val conf = configurationAdmin.getConfiguration(ConfigurationAdmin::class.java.name, null)
//        val tmpDir = args[0]
//        val cpk = Path.of(args[1])
//
//        conf?.update(Hashtable(mapOf("baseDirectory" to tmpDir,
//            "platformVersion" to 999,
//            "blacklistedKeys" to emptyList<Any>())))
//
//        val outputStream = ByteArrayOutputStream()
//        CPI.assemble(outputStream, "cpi", "1.0", listOf(cpk))
//        val loadCpb: CPI = installService.loadCpb(ByteArrayInputStream(outputStream.toByteArray()))
//        val sandboxGroup = sandboxCreationService.createSandboxGroup(loadCpb.cpks.map { it.metadata.hash })
//
//        val factory = SerializerFactoryBuilder.build(AllWhitelist)
//
//        // Save output
////        val output = SerializationOutput(factory)
////        val example =
////            sandboxGroup.loadClassFromMainBundles<Any>("net.corda.applications.examples.amqp.typeevolution.Example", Any::class.java)
////        val method = example.getMethod("saveResourceFiles")
////        val result = method.invoke(example.getConstructor().newInstance()) as Map<*, *>
////        for (i in result) {
////            File(i.key as String).writeBytes(output.serialize(i.value!!, AMQP_STORAGE_CONTEXT.withSandboxGroup(sandboxGroup)).bytes)
////        }
//
//
//        // Test
//        val input = DeserializationInput(factory)
//        runExample(input, AMQP_STORAGE_CONTEXT.withSandboxGroup(sandboxGroup))

        exit()
    }

    private fun prepareSandbox(cpkFiles: List<Path>): SandboxGroup {
        val outputStream = ByteArrayOutputStream()
        CPI.assemble(outputStream, "cpi", "1.0", cpkFiles)
        val loadCpb: CPI = installService.loadCpb(ByteArrayInputStream(outputStream.toByteArray()))
        return sandboxCreationService.createSandboxGroup(loadCpb.cpks.map { it.metadata.hash })
    }

    private fun configureSystem(tmpDir: String) {
        val conf = configurationAdmin.getConfiguration(ConfigurationAdmin::class.java.name, null)
        conf?.update(
            Hashtable(
                mapOf(
                    "baseDirectory" to tmpDir,
                    "platformVersion" to 999,
                    "blacklistedKeys" to emptyList<Any>()
                )
            )
        )
    }

    override fun shutdown() {
        consoleLogger.info("Shutting down")
    }

//    @Suppress("MaxLineLength")
//    private fun runExample(input: DeserializationInput, serializationContext: SerializationContext){
//        consoleLogger.info("AddNullableProperty = " + (input.deserialize(ByteSequence.of(this::class.java.getResource("addNullableProperty.bin")!!.readBytes()), Any::class.java, serializationContext)))
//        consoleLogger.info("AddNonNullableProperty = " + (input.deserialize(ByteSequence.of(this::class.java.getResource("addNonNullableProperty.bin")!!.readBytes()), Any::class.java, serializationContext)))
//        consoleLogger.info("MultipleEvolutions = " + (input.deserialize(ByteSequence.of(this::class.java.getResource("multipleEvolutions.bin")!!.readBytes()), Any::class.java, serializationContext)))
//        consoleLogger.info("MultipleEvolutions = " + (input.deserialize(ByteSequence.of(this::class.java.getResource("multipleEvolutions-2.bin")!!.readBytes()), Any::class.java, serializationContext)))
//        consoleLogger.info("RemovingProperties = " + (input.deserialize(ByteSequence.of(this::class.java.getResource("removingProperties.bin")!!.readBytes()), Any::class.java, serializationContext)))
//        consoleLogger.info("ReorderConstructorParameters = " + (input.deserialize(ByteSequence.of(this::class.java.getResource("reorderConstructorParameters.bin")!!.readBytes()), Any::class.java, serializationContext)))
//        // consoleLogger.info("RenameEnum = " + (input.deserialize(ByteSequence.of(this::class.java.getResource("renameEnum.bin")!!.readBytes()), Any::class.java, serializationContext)))
//        consoleLogger.info("AddEnumValue = " + (input.deserialize(ByteSequence.of(this::class.java.getResource("addEnumValue.bin")!!.readBytes()), Any::class.java, serializationContext)))
//    }

    private fun configureSerialization(
        internalCustomSerializers: List<SerializationCustomSerializer<*, *>>,
        cordappCustomSerializers: List<SerializationCustomSerializer<*, *>>
    ): SerializerFactory {
        // Create SerializerFactory
        val factory = SerializerFactoryBuilder.build(AllWhitelist)
        // Register platform serializers
        for (customSerializer in internalCustomSerializers) {
            factory.register(customSerializer, true)
        }
        // Register CorDapp serializers
        for (customSerializer in cordappCustomSerializers) {
            factory.registerExternal(customSerializer)
        }
        return factory
    }

    /**
     * Prove that we can have two different configurations of serialisation in memory at the same time
     * with different custom serialisers
     */
    private fun differentSerializersPerSandboxGroup() {

        consoleLogger.info("REQUIREMENT - Building serialisation environments with different custom serialisers")
        val sandboxA = configureSerialization(emptyList(), listOf(CustomSerializerA()))
        val sandboxB = configureSerialization(emptyList(), listOf(CustomSerializerB()))

        val outputA = SerializationOutput(sandboxA)
        val outputB = SerializationOutput(sandboxB)

        val inputA = DeserializationInput(sandboxA)
        val inputB = DeserializationInput(sandboxB)

        consoleLogger.info("Check custom serialisers work in environment A")
        val objA = NeedsCustomSerializerExampleA(1)
        val serializedBytesA = outputA.serialize(objA, AMQP_STORAGE_CONTEXT)
        consoleLogger.info("SUCCESS - Serialise successful in environment A")
        val deserializeA = inputA.deserialize(serializedBytesA, AMQP_STORAGE_CONTEXT)
        consoleLogger.info("SUCCESS - Deserialise successful in environment A")
        consoleLogger.info("Original object: $objA")
        consoleLogger.info("Deserialised object: $deserializeA")


        consoleLogger.info("Check custom serialisers work in environment B")
        val objB = NeedsCustomSerializerExampleB(2)
        val serializedBytesB = outputB.serialize(objB, AMQP_STORAGE_CONTEXT)
        consoleLogger.info("SUCCESS - Serialise successful in environment B")
        val deserializeB = inputB.deserialize(serializedBytesB, AMQP_STORAGE_CONTEXT)
        consoleLogger.info("SUCCESS - Deserialise successful in environment B")
        consoleLogger.info("Original object: $objB")
        consoleLogger.info("Deserialised object: $deserializeB")

        consoleLogger.info("Check that environments are independent")
        var worked = false
        try {
            outputA.serialize(objB, AMQP_STORAGE_CONTEXT)
        } catch (e: MissingSerializerException) {
            consoleLogger.info("SUCCESS - Environment A does not have serializer from environment B")
            worked = true
        } finally {
            if (!worked)
                consoleLogger.info("FAIL - Environment A has serializer from environment B")
            worked = false
        }

        try {
            outputB.serialize(objA, AMQP_STORAGE_CONTEXT)
        } catch (e: MissingSerializerException) {
            consoleLogger.info("SUCCESS - Environment B does not have serializer from environment A")
            worked = true
        } finally {
            if (!worked)
                consoleLogger.info("FAIL - Environment B has serializer from environment A")
        }
    }

    /**
     * Prove that we can't replace the platform serialisers
     */
    private fun platformTakesPriority() {
        consoleLogger.info("REQUIREMENT - Check that platform serialisers take priority over CorDapp serialisers")
        consoleLogger.info("Difference from my earlier expectation - Throws exception instead of priority/log message")
        consoleLogger.info("Only when we attempt to work with serialiser target type")
        consoleLogger.info("This is stricter than I expected and comes out of existing behaviour. I believe this is acceptable.")

        consoleLogger.info("Attempt to override platform serialiser:")

        val factory = configureSerialization(listOf(CustomSerializerA()), listOf(CustomSerializerA()))
        val output = SerializationOutput(factory)

        val obj = NeedsCustomSerializerExampleA(5)

        var worked = false
        try {
            output.serialize(obj, AMQP_STORAGE_CONTEXT)
        } catch (e: DuplicateCustomSerializerException) {
            consoleLogger.info("SUCCESS - Exception thrown attempting to replace platform serialiser:")
            consoleLogger.info(e.message)
            worked = true
        }
        finally {
            if (!worked)
                consoleLogger.info("FAIL - System didn't notice we replaced platform serialiser")
        }

    }

    /**
     * Prove that we notify the end user when they try to override platform serialiser
     */
    private fun logMessageIfAttemptToReplacePlatform() {
        consoleLogger.info("REQUIREMENT - Log message warning written if CorDapp attempts to replace platform serializer")
        consoleLogger.info("- Throws exception instead")
    }

    /**
     * Have a method of registering platform serialisers
     */
    private fun registerInternalSerializers() {
        consoleLogger.info("REQUIREMENT - Register the list of internal serializers for use by the sandbox")
        consoleLogger.info("- See configureSerialization for example")
    }

    /**
     * Have a method of registering CorDapp serialisers
     */
    private fun registerCorDappSerializers() {
        consoleLogger.info("REQUIREMENT - Register the list of CorDapp custom serializers for use by the sandbox")
        consoleLogger.info("- See configureSerialization for example")
    }

}