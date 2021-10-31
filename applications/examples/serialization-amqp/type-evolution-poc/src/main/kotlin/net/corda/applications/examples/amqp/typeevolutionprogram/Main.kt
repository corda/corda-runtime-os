package net.corda.applications.examples.amqp.typeevolutionprogram

import net.corda.install.InstallService
import net.corda.internal.serialization.AllWhitelist
import net.corda.internal.serialization.ByteBufferOutputStream
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.packaging.CPI
import net.corda.sandbox.SandboxCreationService
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

val factory = SerializerFactoryBuilder.build(AllWhitelist)
val output = SerializationOutput(factory)
val input = DeserializationInput(factory)

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
//    saveResourceFiles()

        val conf = configurationAdmin.getConfiguration(ConfigurationAdmin::class.java.name, null)
        val tmpDir = args[0]
        val cpk = Path.of(args[1])

        conf?.update(Hashtable(mapOf("baseDirectory" to tmpDir,
            "platformVersion" to 999,
            "blacklistedKeys" to emptyList<Any>())))

//        printBundleList()
//        printServiceList()

//        sandboxCreationService.createPublicSandbox()
//        sandboxCreationService.createSandboxGroup()

        val outputStream = ByteArrayOutputStream()
        CPI.assemble(outputStream, "cpi", "1.0", listOf(cpk))
        consoleLogger.info("loadCpb")
        val loadCpb: CPI = installService.loadCpb(ByteArrayInputStream(outputStream.toByteArray()))
        consoleLogger.info("createSandboxGroup")
        val sandboxGroup = sandboxCreationService.createSandboxGroup(loadCpb.cpks.map { it.metadata.hash })

        consoleLogger.info(sandboxGroup.toString())

        exit()
    }

    private fun printServiceList() {
        val allServiceReferences = bundleContext.getAllServiceReferences(null, null)
        consoleLogger.info("getAllServiceReferences:")
        consoleLogger.info("------------------------")
        allServiceReferences.forEach { consoleLogger.info(it.toString()) }
    }

    private fun printBundleList() {
        val allBundles = bundleContext.bundles.toList()

        consoleLogger.info("allBundles:")
        consoleLogger.info("------------------------")
        allBundles.forEach {
            consoleLogger.info(it.toString())
            it.registeredServices?.forEach { service -> consoleLogger.info("---> $service") }
        }
    }

    override fun shutdown() {
        consoleLogger.info("Shutting down")
    }
}





