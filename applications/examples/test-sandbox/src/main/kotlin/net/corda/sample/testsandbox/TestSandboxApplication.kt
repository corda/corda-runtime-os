package net.corda.sample.testsandbox

import net.corda.install.InstallService
import net.corda.lifecycle.SimpleLifeCycleCoordinator
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.osgi.api.Application
import net.corda.sandbox.SandboxService
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Paths

@Component(immediate = true)
class TestSandboxApplication : Application {

    private companion object {

        private val logger: Logger = LoggerFactory.getLogger(TestSandboxApplication::class.java)

        private const val PATH = "applications/examples/test-cpk/build/libs"

    } //~ companion object

    @Reference
    private var installService: InstallService? = null

    @Reference
    private var sandboxService: SandboxService? = null

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

    override fun startup(args: Array<String>) {
        logger.info("Start-up...")
        if (installService != null) {
            logger.info("Install service active.")
            if (sandboxService != null) {
                logger.info("Sandbox service active.")
                coordinator.start()
            }
        }
    }

    override fun shutdown() {
        logger.info("Shutdown.")
        coordinator.stop()
    }
}