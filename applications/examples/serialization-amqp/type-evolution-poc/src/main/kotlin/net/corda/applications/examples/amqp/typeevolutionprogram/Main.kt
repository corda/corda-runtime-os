package net.corda.applications.examples.amqp.typeevolutionprogram

import net.corda.internal.serialization.AllWhitelist
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.sandbox.SandboxCreationService
import org.osgi.framework.FrameworkUtil
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
    private val configurationAdmin: ConfigurationAdmin
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
        val cpk = args[1]

        conf?.update(Hashtable(mapOf("baseDirectory" to tmpDir)))

        printBundleList()
        printServiceList()

//        sandboxCreationService.createPublicSandbox()
//        sandboxCreationService.createSandboxGroup()

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





