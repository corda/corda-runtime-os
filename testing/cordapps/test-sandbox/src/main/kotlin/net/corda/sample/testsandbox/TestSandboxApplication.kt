package net.corda.sample.testsandbox

import net.corda.install.InstallService
import net.corda.lifecycle.SimpleLifeCycleCoordinator
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.osgi.api.Application
import net.corda.sandbox.SandboxService
import net.corda.v5.base.util.contextLogger
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.service.component.annotations.Activate
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
 *
 * @param configAdmin       Set automatically because both [ConfigurationAdmin] and this class are OSGi components.
 * @param installService    Set automatically because both [InstallService] and this class are OSGi components.
 * @param sandboxService    Set automatically because both [SandboxService] and this class are OSGi components.
 */
@Component(immediate = true)
class TestSandboxApplication @Activate constructor(
    @Reference
    private val configAdmin: ConfigurationAdmin,
    @Reference(service = InstallService::class)
    private val installService: InstallService,
    @Reference(service = SandboxService::class)
    private val sandboxService: SandboxService,
) : Application {

    private companion object {

        private val logger = contextLogger()

    } //~ companion object

    /**
     * Coordinator used start the [TestSandbox] as a Corda component, only when [installService] and [sandboxService]
     * are set because service publishing and wiring is done before [startup] is called.
     */
    private lateinit var coordinator: SimpleLifeCycleCoordinator

    /**
     * This method is called by after the OSGI framework wired all bundles and services.
     *
     * Properties annotated with `@Reference` are already set when this method is called.
     */
    override fun startup(args: Array<String>) {
        val path = args[0]
        logger.info("Start-up loading CPKs at $path...")
        val configuration = configAdmin.getConfiguration(ConfigurationAdmin::class.java.name, null)
        val configProperties: Dictionary<String, Any> = Hashtable()
        configProperties.put("baseDirectory", path)
        configProperties.put("blacklistedKeys", emptyList<String>())
        configProperties.put("platformVersion", 5)
        configuration.update(configProperties)
        logger.info("Configuration.properties ${configuration.properties} set.")
        coordinator = SimpleLifeCycleCoordinator(1, 1000L) { event, _ ->
            val testSandbox = TestSandbox(Paths.get(path), installService, sandboxService)
            when (event) {
                is StartEvent -> {
                    testSandbox.start()
                }
                is StopEvent -> {
                    testSandbox.stop()
                }
            }
        }
        coordinator.start()
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