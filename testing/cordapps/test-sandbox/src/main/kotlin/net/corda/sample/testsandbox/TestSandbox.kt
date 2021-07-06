package net.corda.sample.testsandbox

import net.corda.install.InstallService
import net.corda.lifecycle.LifeCycle
import net.corda.packaging.Cpb
import net.corda.sandbox.SandboxService
import net.corda.v5.base.util.contextLogger
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList

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

    private fun assembleCPB(cpkUrlList: List<URL>): Cpb {
        val cpkList = cpkUrlList.map { url ->
            val urlAsString = url.toString()
            val cpkName = urlAsString.substring(urlAsString.lastIndexOf("/") + 1)
            val cpkFile = Files.createTempFile(cpkName, ".cpk")
            Files.newOutputStream(cpkFile).use {
                url.openStream().copyTo(it)
            }
            cpkFile.toAbsolutePath()
        }.toList()
        val tempFile = Files.createTempFile("dummy-cordapp-bundle", ".cpb")
        return try {
            Files.newOutputStream(tempFile).use { outputStream ->
                Cpb.assemble(outputStream, cpkList)
            }
            installService.loadCpb(Files.newInputStream(tempFile))
        } finally {
            Files.delete(tempFile)
        }
    }

    private fun listCPK(path: Path): List<URL> {
        return Files.list(path).filter {
            it.toFile().name.endsWith(".cpk")
        }.map {
            it.toUri().toURL()
        }.toList()
    }

    fun installCPK(path: Path) {
        val cpkUrlList = listCPK(path)
        val cpb = assembleCPB(cpkUrlList)
        val sandboxGroup = sandboxService.createSandboxes(cpb.identifier)
        sandboxGroup.sandboxes.forEach { sandbox ->
            val cls = sandbox.loadClass("net.corda.sample.testcpk.TestCPK")
            val constructor = cls.getConstructor()
            val obj = constructor.newInstance() as Runnable
            obj.run()
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
            installCPK(path)
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