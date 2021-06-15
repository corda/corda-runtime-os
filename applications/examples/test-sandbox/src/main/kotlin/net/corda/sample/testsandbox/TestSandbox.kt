package net.corda.sample.testsandbox

import net.corda.install.Cpi
import net.corda.install.InstallService
import net.corda.lifecycle.LifeCycle
import net.corda.sandbox.SandboxService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path

class TestSandbox(
    private val path: Path,
    private var installService: InstallService,
    private var sandboxService: SandboxService,
) : LifeCycle {

    private companion object {

        private val logger: Logger = LoggerFactory.getLogger(TestSandbox::class.java)

    } //~ companion object


    @Volatile
    private var _isRunning: Boolean = false

    override val isRunning: Boolean get() = _isRunning

    fun installCpk(path: Path) {
        logger.info("Install CPKs from $path.")
        val cpiIdentifier = "unique_cpi_identifier"
        val cpkUris = installService.scanForCpks(setOf(path.toUri()))
        val cpis = if (cpkUris.isNotEmpty()) {
            val cpkUriList = cpkUris.toList()
            listOf(Cpi(cpiIdentifier, cpkUriList[0], cpkUriList.drop(1).toSet(), emptyMap()))
        } else {
            emptyList()
        }
        cpis.forEach { cpi ->
            installService.installCpi(cpi)
            val loadedCpi = installService.getCpi(cpiIdentifier)
                ?: throw IllegalArgumentException("CPI $cpiIdentifier has not been installed.")
            logger.info("CPI $loadedCpi loaded.")
            val sandbox = sandboxService.createSandbox(cpiIdentifier)
            logger.info("Sandbox $sandbox active.")
        }
    }

    @Synchronized
    override fun start() {
        if (!isRunning) {
            _isRunning = true
            logger.info("START")
            installCpk(path)
        }

    }

    @Synchronized
    override fun stop() {
        if (isRunning) {
            logger.info("STOP")
            _isRunning = false
        }
    }
}