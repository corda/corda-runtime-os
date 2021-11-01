package net.corda.applications.examples.amqp.typeevolutionprogram

import net.corda.install.InstallService
import net.corda.internal.serialization.AMQP_STORAGE_CONTEXT
import net.corda.internal.serialization.AllWhitelist
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.packaging.CPI
import net.corda.sandbox.SandboxCreationService
import net.corda.v5.base.types.ByteSequence
import net.corda.v5.serialization.SerializationContext
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

    override fun startup(args: Array<String>) {

        // Prepare a sandbox group
        val conf = configurationAdmin.getConfiguration(ConfigurationAdmin::class.java.name, null)
        val tmpDir = args[0]
        val cpk = Path.of(args[1])

        conf?.update(Hashtable(mapOf("baseDirectory" to tmpDir,
            "platformVersion" to 999,
            "blacklistedKeys" to emptyList<Any>())))

        val outputStream = ByteArrayOutputStream()
        CPI.assemble(outputStream, "cpi", "1.0", listOf(cpk))
        val loadCpb: CPI = installService.loadCpb(ByteArrayInputStream(outputStream.toByteArray()))
        val sandboxGroup = sandboxCreationService.createSandboxGroup(loadCpb.cpks.map { it.metadata.hash })

        val factory = SerializerFactoryBuilder.build(AllWhitelist)

        // Save output
//        val output = SerializationOutput(factory)
//        val example =
//            sandboxGroup.loadClassFromMainBundles<Any>("net.corda.applications.examples.amqp.typeevolution.Example", Any::class.java)
//        val method = example.getMethod("saveResourceFiles")
//        val result = method.invoke(example.getConstructor().newInstance()) as Map<*, *>
//        for (i in result) {
//            File(i.key as String).writeBytes(output.serialize(i.value!!, AMQP_STORAGE_CONTEXT.withSandboxGroup(sandboxGroup)).bytes)
//        }


        // Test
        val input = DeserializationInput(factory)
        runExample(input, AMQP_STORAGE_CONTEXT.withSandboxGroup(sandboxGroup))

        exit()
    }

    override fun shutdown() {
        consoleLogger.info("Shutting down")
    }

    @Suppress("MaxLineLength")
    private fun runExample(input: DeserializationInput, serializationContext: SerializationContext){
        consoleLogger.info("AddNullableProperty = " + (input.deserialize(ByteSequence.of(this::class.java.getResource("addNullableProperty.bin")!!.readBytes()), Any::class.java, serializationContext)))
        consoleLogger.info("AddNonNullableProperty = " + (input.deserialize(ByteSequence.of(this::class.java.getResource("addNonNullableProperty.bin")!!.readBytes()), Any::class.java, serializationContext)))
        consoleLogger.info("MultipleEvolutions = " + (input.deserialize(ByteSequence.of(this::class.java.getResource("multipleEvolutions.bin")!!.readBytes()), Any::class.java, serializationContext)))
        consoleLogger.info("MultipleEvolutions = " + (input.deserialize(ByteSequence.of(this::class.java.getResource("multipleEvolutions-2.bin")!!.readBytes()), Any::class.java, serializationContext)))
        consoleLogger.info("RemovingProperties = " + (input.deserialize(ByteSequence.of(this::class.java.getResource("removingProperties.bin")!!.readBytes()), Any::class.java, serializationContext)))
        consoleLogger.info("ReorderConstructorParameters = " + (input.deserialize(ByteSequence.of(this::class.java.getResource("reorderConstructorParameters.bin")!!.readBytes()), Any::class.java, serializationContext)))
        // consoleLogger.info("RenameEnum = " + (input.deserialize(ByteSequence.of(this::class.java.getResource("renameEnum.bin")!!.readBytes()), Any::class.java, serializationContext)))
        consoleLogger.info("AddEnumValue = " + (input.deserialize(ByteSequence.of(this::class.java.getResource("addEnumValue.bin")!!.readBytes()), Any::class.java, serializationContext)))
    }

}
