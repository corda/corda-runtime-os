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
import net.corda.v5.serialization.MissingSerializerException
import net.corda.v5.serialization.SerializationCustomSerializer
import org.osgi.framework.FrameworkUtil
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
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

@Suppress("MaxLineLength")
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
    @Reference(cardinality = ReferenceCardinality.MULTIPLE)
    private val internalCustomSerializers: List<SerializationCustomSerializer<*, *>>
) : Application {
    private companion object {
        private val consoleLogger: Logger = LoggerFactory.getLogger("Console")
    }

    private val bundle = FrameworkUtil.getBundle(this::class.java)

    private fun exit() = shutDownService.shutdown(bundle)

    override fun startup(args: Array<String>) {

        val tmpDir = args[0]
        // Prepare a sandbox group
        configureSystem(tmpDir)

        val cpkA = Path.of(args[1])
        val cpkB = Path.of(args[2])
        val sandboxA = prepareSandbox(listOf(cpkA))
        val sandboxB = prepareSandbox(listOf(cpkB))

        differentSerializersPerSandboxGroup(sandboxA, sandboxB)
        consoleLogger.info("------------------------------------------------")
        platformTakesPriority(sandboxA)
        consoleLogger.info("------------------------------------------------")
        logMessageIfAttemptToReplacePlatform()
        consoleLogger.info("------------------------------------------------")
        registerInternalSerializers()
        consoleLogger.info("------------------------------------------------")
        registerCorDappSerializers()
        consoleLogger.info("------------------------------------------------")

        exit()
    }

    data class SandboxAndSerializers(val sandboxGroup: SandboxGroup, val serializers: List<String>)

    private fun prepareSandbox(cpkFiles: List<Path>): SandboxAndSerializers {
        val outputStream = ByteArrayOutputStream()
        CPI.assemble(outputStream, "cpi", "1.0", cpkFiles)
        val loadCpb: CPI = installService.loadCpb(ByteArrayInputStream(outputStream.toByteArray()))
        val serializers: List<String> = loadCpb.metadata.cpks.flatMap { it.cordappManifest.serializers }
        return SandboxAndSerializers(sandboxCreationService.createSandboxGroup(loadCpb.cpks), serializers)
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

    private fun configureSerialization(sandboxAndSerializers: SandboxAndSerializers): SerializerFactory {
        // Create SerializerFactory
        val factory = SerializerFactoryBuilder.build(AllWhitelist)
        // Register platform serializers
        for (customSerializer in internalCustomSerializers) {
            consoleLogger.info("Registering internal serializer " + customSerializer.javaClass.name)
            factory.register(customSerializer, true)
        }
        // Build CorDapp serializers
        val cordappCustomSerializers = sandboxAndSerializers.serializers.map {
            val classFromMainBundles = sandboxAndSerializers.sandboxGroup.loadClassFromMainBundles(it, SerializationCustomSerializer::class.java)
            classFromMainBundles.getConstructor().newInstance()
        }
        // Register CorDapp serializers
        for (customSerializer in cordappCustomSerializers) {
            consoleLogger.info("Registering CorDapp serializer " + customSerializer.javaClass.name)
            factory.registerExternal(customSerializer)
        }
        return factory
    }

    /**
     * Prove that we can have two different configurations of serialisation in memory at the same time
     * with different custom serialisers
     */
    private fun differentSerializersPerSandboxGroup(sandboxA: SandboxAndSerializers, sandboxB: SandboxAndSerializers) {

        consoleLogger.info("REQUIREMENT - Building serialisation environments with different custom serialisers")
        consoleLogger.info("sandboxA")
        val serializationA = configureSerialization(sandboxA)
        consoleLogger.info("sandboxB")
        val serializationB = configureSerialization(sandboxB)

        val outputA = SerializationOutput(serializationA)
        val outputB = SerializationOutput(serializationB)

        val inputA = DeserializationInput(serializationA)
        val inputB = DeserializationInput(serializationB)

        consoleLogger.info("Check custom serialisers work in environment A")
        val objA = sandboxA.sandboxGroup.loadClassFromMainBundles("net.corda.applications.examples.amqp.customserializer.examplea.NeedsCustomSerializerExampleA", Any::class.java).getConstructor(Integer.TYPE).newInstance(1)
        val contextA = AMQP_STORAGE_CONTEXT.withSandboxGroup(sandboxA)
        val serializedBytesA = outputA.serialize(objA, contextA)
        consoleLogger.info("SUCCESS - Serialise successful in environment A")
        val deserializeA = inputA.deserialize(serializedBytesA, contextA)
        consoleLogger.info("SUCCESS - Deserialise successful in environment A")
        consoleLogger.info("Original object: $objA")
        consoleLogger.info("Deserialised object: $deserializeA")


        consoleLogger.info("Check custom serialisers work in environment B")
        val objB = sandboxB.sandboxGroup.loadClassFromMainBundles("net.corda.applications.examples.amqp.customserializer.exampleb.NeedsCustomSerializerExampleB", Any::class.java).getConstructor(Int::class.java).newInstance(2)
        val contextB = AMQP_STORAGE_CONTEXT.withSandboxGroup(sandboxB)
        val serializedBytesB = outputB.serialize(objB, contextB)
        consoleLogger.info("SUCCESS - Serialise successful in environment B")
        val deserializeB = inputB.deserialize(serializedBytesB, contextB)
        consoleLogger.info("SUCCESS - Deserialise successful in environment B")
        consoleLogger.info("Original object: $objB")
        consoleLogger.info("Deserialised object: $deserializeB")

        consoleLogger.info("Check that environments are independent")
        var worked = false
        try {
            outputA.serialize(objB, contextA)
        } catch (e: MissingSerializerException) {
            consoleLogger.info("SUCCESS - Environment A does not have serializer from environment B")
            worked = true
        } finally {
            if (!worked)
                consoleLogger.info("FAIL - Environment A has serializer from environment B")
            worked = false
        }

        try {
            outputB.serialize(objA, contextB)
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
    private fun platformTakesPriority(sandboxA: SandboxAndSerializers) {
        consoleLogger.info("REQUIREMENT - Check that platform serialisers take priority over CorDapp serialisers")
        consoleLogger.info("Difference from my earlier expectation - Throws exception instead of priority/log message")
        consoleLogger.info("Only when we attempt to work with serialiser target type")
        consoleLogger.info("This is stricter than I expected and comes out of existing behaviour. I believe this is acceptable.")

        consoleLogger.info("Attempt to override platform serialiser:")

        // Create SerializerFactory
        val factory = SerializerFactoryBuilder.build(AllWhitelist)
        // Build CorDapp serializers
        val cordappCustomSerializers = sandboxA.serializers.map {
            val classFromMainBundles = sandboxA.sandboxGroup.loadClassFromMainBundles(it, SerializationCustomSerializer::class.java)
            classFromMainBundles.getConstructor().newInstance()
        }
        // Register platform serializers
        for (customSerializer in internalCustomSerializers) {
            factory.register(customSerializer, true)
        }
        // Register SandBox A custom serializers as platform serializers and CorDapp serialisers
        for (customSerializer in cordappCustomSerializers) {
            factory.register(customSerializer, true)
            factory.registerExternal(customSerializer)
        }
        val output = SerializationOutput(factory)

        // Build test object
        val obj = sandboxA.sandboxGroup.loadClassFromMainBundles("net.corda.applications.examples.amqp.customserializer.examplea.NeedsCustomSerializerExampleA", Any::class.java).getConstructor(Integer.TYPE).newInstance(5)

        // Run object through serialization
        var worked = false
        try {
            output.serialize(obj, AMQP_STORAGE_CONTEXT.withSandboxGroup(sandboxA.sandboxGroup))
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