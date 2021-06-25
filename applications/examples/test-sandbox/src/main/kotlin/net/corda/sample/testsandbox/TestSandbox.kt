package net.corda.sample.testsandbox

import net.corda.install.Cpi
import net.corda.install.Cpk
import net.corda.install.CpiIdentifier
import net.corda.install.InstallService
import net.corda.lifecycle.LifeCycle
import net.corda.sandbox.SandboxService
import net.corda.v5.base.util.contextLogger
import java.nio.file.Path
import java.util.*

/**
 * This class shows how to load Cordapps and create the [net.corda.sandbox.Sandbox] for it.
 *
 * This class implements [LifeCycle] because it is intended to be a Corda component.
 */
class TestSandbox(
    /**
     * Path where CPK artifacts are loaded.
     */
    private val path: Path,

    /**
     * Injected as OSGi service by [TestSandboxApplication].
     */
    private var installService: InstallService,

    /**
     * Injected as OSGi service by [TestSandboxApplication].
     */
    private var sandboxService: SandboxService,
) : LifeCycle {

    private companion object {

        private val logger = contextLogger()

    } //~ companion object


    /**
     * `true` if this Corda component is running.
     *
     * @see [start]
     * @see [stop]
     */
    @Volatile
    private var _isRunning: Boolean = false

    /**
     * Return `true` if this Corda component is running.
     */
    override val isRunning: Boolean get() = _isRunning

    /**
     * Scan the [path] to load the CPK artifacts found.
     * Print info about the [net.corda.sandbox.Sandbox] created per CPK.
     */
    fun installCpk(path: Path) {
        logger.info("Install CPKs from $path.")
        val cpiIdentifier = CpiIdentifier(UUID.randomUUID().toString())
        val cpkUris = installService.scanForCpks(setOf(path.toUri()))
        val cpis = if (cpkUris.isNotEmpty()) {
            val cpkUriList = cpkUris.toList()
            listOf(Cpi(cpiIdentifier, cpkUriList[0], cpkUriList.drop(1).toSet(), emptyMap()))
        } else {
            emptyList()
        }
        cpis.forEach { cpi ->
            val cpkSet = installService.loadCpi(cpi)
            val sandbox = sandboxService.createSandboxes(cpiIdentifier)
            logger.info("Sandbox $sandbox active.")
            val cpk: Cpk? = cpkSet.find { it.id.cordappSymbolicName == "net.corda.test-cpk" }
            if (cpk != null) {
                val cls = sandbox.loadClass(cpk.id, "net.corda.sample.testcpk.TestCPK")
                logger.info("Class $cls.")
                val obj = cls.getDeclaredConstructor().newInstance()
                val method = cls.getMethod("test")
                method.invoke(obj)
            }
        }
    }

    /**
     * Start this Corda component, called by [TestSandboxApplication.coordinator].
     */
    @Synchronized
    override fun start() {
        if (!isRunning) {
            _isRunning = true
            logger.info("Starting...")
            installCpk(path)
            logger.info("Started.")
        }
    }

    /**
     * Stop this Corda component, called by [TestSandboxApplication.coordinator].
     */
    @Synchronized
    override fun stop() {
        if (isRunning) {
            logger.info("Stop.")
            _isRunning = false
        }
    }

}