package net.corda.sample.testsandbox

import net.corda.install.InstallService
import net.corda.lifecycle.SimpleLifeCycleCoordinator
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.osgi.api.Application
import net.corda.sandbox.SandboxService
import net.corda.v5.base.util.contextLogger
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.nio.file.Paths
import java.util.*

/**
 * This class is the entry point of the didactic application showing how to run CordApps from a bootable JAR.
 *
 * The CordApp used in this demo application is in the module `:applications:examples:test-cpk`.
 *
 * Run
 *
 * ```
 * ./gradlew :applications:examples:test-cpk:cpk
 * ```
 *
 * to create tke CPK artifact used in this Application.
 */
@Component(immediate = true)
class TestSandboxApplication : Application {

    @Suppress("MaxLineLength")
    private companion object {

        private val logger = contextLogger()

        /**
         * Relative path to the directory where the CPK artifact is created
         * from the `:applications:examples:test-cpk` module.
         */
        private const val PATH = "applications/examples/test-cpk/build/libs"
//        private const val PATH = "/home/ldebiasi/IdeaProjects/corda5/testing/cordapps/fungiblestate-sample/fungiblestate-contracts/build/libs"

    } //~ companion object

    @Reference
    private var configAdmin: ConfigurationAdmin? = null

    /**
     * Reference to the [InstallService] set automatically because both [InstallService] and
     * this class are annotated as `@Component` and they are published OSGi services.
     */
    @Reference(service = InstallService::class)
    private var installService: InstallService? = null

    /**
     * Reference to the [SandboxService] set automatically because both [SandboxService] and
     * this class are annotated as `@Component` and they are published OSGi services.
     */
    @Reference(service = SandboxService::class)
    private var sandboxService: SandboxService? = null

    /**
     * Coordinator used start the [TestSandbox] as a Corda component, only when [installService] and [sandboxService]
     * are set because service publishing and wiring is done before [startup] is called.
     */
    private val coordinator = SimpleLifeCycleCoordinator(1, 1000L) { event, _ ->
        val testSandbox = TestSandbox(Paths.get(PATH), installService!!, sandboxService!!)
        when (event) {
            is StartEvent -> {
                testSandbox.start()
            }
            is StopEvent -> {
                testSandbox.stop()
            }
        }
    }

    /**
     * This method is called by after the OSGI framework wired all bundles and services.
     *
     * Properties annotated with `@Reference` are already set when this method is called.
     */
    override fun startup(args: Array<String>) {
        logger.info("Start-up.")

        if (configAdmin != null) {
            logger.info("Configuration Admin active.")
            val configuration = configAdmin!!.getConfiguration(ConfigurationAdmin::class.java.name, null)
            logger.info("Configuration $configuration")
            val configProperties: Dictionary<String, Any> = Hashtable()
            configProperties.put("baseDirectory", PATH)
            val blacklistedKeys: List<String> = emptyList()
            configProperties.put("blacklistedKeys", blacklistedKeys)
            configProperties.put("platformVersion", 5)
            configuration.update(configProperties)
            logger.info("Configuration.properties ${configuration.properties}")
        } else {
            logger.warn("Configuration Admin not found.")
        }
        if (installService != null) {
            logger.info("Install service active.")
            if (sandboxService != null) {
                logger.info("Sandbox service active.")
                //coordinator.start()
                val path = Paths.get(PATH)
                TestSandbox(path, installService!!, sandboxService!!).installCpk(path)
            } else {
                logger.warn("Sandbox service not found.")
            }
        } else {
            logger.warn("Install service not found.")
        }
        logger.info("Press [CTRL+C] to stop the application...")
    }

    /**
     * This method is called when the bootable JAr is requested to terminate by the application itself or the
     * operating system because the JVM terminates.
     */
    override fun shutdown() {
        logger.info("Shutdown.")
        coordinator.stop()
    }

}