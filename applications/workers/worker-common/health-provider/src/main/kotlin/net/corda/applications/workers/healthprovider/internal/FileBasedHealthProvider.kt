package net.corda.applications.workers.healthprovider.internal

import net.corda.applications.workers.healthprovider.FILE_HEALTH_PROVIDER
import net.corda.applications.workers.healthprovider.HealthProvider
import org.osgi.service.component.annotations.Component
import java.io.File

/**
 * With this health provider, the worker is considered healthy if and only if [healthCheckFile] file exists. Docker
 * Healthcheck and Kubernetes liveness probes can therefore monitor the health of the worker using the command
 * `cat [healthCheckFile]`, which will return 0 if the file exists, and 1 otherwise.
 *
 * In the same way, a [readinessCheckFile] is used to ascertain worker readiness.
 *
 * Since these files do not exist initially, the worker starts in an unhealthy, not-ready state.
 */
@Component(service = [HealthProvider::class], property = [FILE_HEALTH_PROVIDER])
@Suppress("Unused")
internal class FileBasedHealthProvider : HealthProvider {
    private val tempDir = System.getProperty("java.io.tmpdir")
    private val healthCheckFile = File("$tempDir/$HEALTH_CHECK_PATH_NAME")
    private val readinessCheckFile = File("$tempDir/$READINESS_CHECK_PATH_NAME")

    init {
        setOf(healthCheckFile, readinessCheckFile).forEach { file -> file.deleteOnExit() }
    }

    /** Marks the worker as healthy by creating the health-check file. */
    override fun setHealthy() {
        healthCheckFile.createNewFile()
    }

    /** Marks the worker as unhealthy by deleting the health-check file. */
    override fun setNotHealthy() {
        healthCheckFile.delete()
    }

    /** Marks the worker as ready by creating the readiness-check file. */
    override fun setReady() {
        readinessCheckFile.createNewFile()
    }

    /** Marks the worker as not ready by deleting the readiness-check file. */
    override fun setNotReady() {
        readinessCheckFile.delete()
    }
}