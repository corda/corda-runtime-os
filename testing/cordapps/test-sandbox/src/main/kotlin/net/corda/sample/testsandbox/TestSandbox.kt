package net.corda.sample.testsandbox

import net.corda.install.InstallService
import net.corda.lifecycle.LifeCycle
import net.corda.packaging.Cpb
import net.corda.sandbox.SandboxGroup
import net.corda.sandbox.SandboxService
import net.corda.v5.base.util.contextLogger
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList

/**
 * This class shows how to run CordApps.
 *
 * The class supposes CPKs are at [path] and CPKs expose [Runnable] implementations having [runnableFQN]
 * full qualified name.
 *
 * This class implements [LifeCycle] because it is intended to be a Corda component.
 *
 * The [start] method runs [runnableFQN] implementations of [Runnable] in CPKs at [path].
 *
 */
class TestSandbox(
    /**
     * Path where CPK artifacts are loaded.
     */
    private val path: Path,

    /**
     * Full qualified name of the class exposed in CPKs and implementing [Runnable] used to run the CPK.
     */
    private val runnableFQN: String,

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

        /**
         * Corda Package Bundle Extension.
         *
         * @see [assembleCPB]
         */
        private const val CPB_EXTENSION = ".cpb"

        /**
         * Corda Package Bundle temporary files prefix.
         *
         * @see [assembleCPB]
         */
        private const val CPB_PREFIX = "corda_package_bundle_"

        /**
         * CordApp extension.
         *
         * @see [assembleCPB]
         * @see [listCPK]
         */
        private const val CPK_EXTENSION = ".cpk"

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
     * Return the [Cpb] assembled from the CPKs listed in [cpkUrlList].
     *
     * The [Cpb] artifact is assembled in a temporary file removed once the operation completed (regularly or not).
     * The assembled [Cpb] is installed in the Corda node.
     *
     * @return the [Cpb] assembled from the CPKs listed in [cpkUrlList].
     *
     */
    private fun assembleCPB(cpkUrlList: List<URL>): Cpb {
        val cpkList = cpkUrlList.map { url ->
            val urlAsString = url.toString()
            val cpkName = urlAsString.substring(urlAsString.lastIndexOf("/") + 1)
            val cpkFile = Files.createTempFile(cpkName, CPK_EXTENSION)
            Files.newOutputStream(cpkFile).use {
                url.openStream().copyTo(it)
            }
            cpkFile.toAbsolutePath()
        }.toList()
        val tempFile = Files.createTempFile(CPB_PREFIX, CPB_EXTENSION)
        return try {
            Files.newOutputStream(tempFile).use { outputStream ->
                Cpb.assemble(outputStream, cpkList)
            }
            installService.loadCpb(Files.newInputStream(tempFile))
        } finally {
            Files.delete(tempFile)
        }
    }

    /**
     * Return the list of URL addressing the CPKs at [path].
     *
     * @return the list of URL addressing the CPKs at [path].
     */
    private fun listCPK(path: Path): List<URL> {
        return Files.list(path).filter {
            it.toFile().name.endsWith(CPK_EXTENSION)
        }.map {
            it.toUri().toURL()
        }.toList()
    }

    /**
     *  Scan the [path] to install the Corda node all the CPK files in a [SandboxGroup]
     *  and run those CPK exposing the class having [runnableFQN] full qualified name and implementing [Runnable].
     *
     *  @see [assembleCPB]
     *  @see [listCPK]
     */
    private fun runCPK(path: Path, runnableFQN: String) {
        val cpkUrlList = listCPK(path)
        val cpb = assembleCPB(cpkUrlList)
        val sandboxGroup: SandboxGroup = sandboxService.createSandboxes(cpb.identifier)
        sandboxGroup.sandboxes.forEach { sandbox ->
            val cls = sandbox.loadClass(runnableFQN)
            val constructor = cls.getConstructor()
            val obj = constructor.newInstance() as Runnable
            obj.run()
        }
    }

    /**
     * Start this Corda component, called by [TestSandboxApplication.coordinator] and run CPKs at [path].
     *
     * @see [runCPK]
     */
    @Synchronized
    override fun start() {
        if (!isRunning) {
            _isRunning = true
            logger.info("Starting...")
            runCPK(path, runnableFQN)
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