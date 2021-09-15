package net.corda.install.internal.driver

import net.corda.install.DriverInstallationException
import net.corda.install.internal.JAR_EXTENSION
import net.corda.install.internal.utilities.BundleUtils
import net.corda.install.internal.utilities.FileUtils
import net.corda.v5.base.util.contextLogger
import org.osgi.framework.BundleException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.nio.file.Path

/** Discovers and installs drivers for an OSGi node. */
@Component(service = [DriverInstaller::class], immediate = true)
internal class DriverInstaller @Activate constructor(
        @Reference
        private val fileUtils: FileUtils,
        @Reference
        private val bundleUtils: BundleUtils
) {

    companion object {
        private val logger = contextLogger()
    }

    /**
     * Searches the [driverDirectories] for JARs, and installs them as bundles. Sub-folders are not searched.
     *
     * A [DriverInstallationException] is thrown if a bundle fails to install or start, unless the failure is because
     * a bundle with the same symbolic name and version is already installed.
     */
    fun installDrivers(driverDirectories: Collection<Path>) {
        val distinctDirectories = driverDirectories.distinct()
        logger.info("Looking for driver JARs in $distinctDirectories.")
        val driverJars = fileUtils.getFilesWithExtension(distinctDirectories, JAR_EXTENSION)

        logger.info("Found and installing these driver JARs: $driverJars.")
        driverJars.mapNotNull { jar ->
            try {
                bundleUtils.installAsBundle(jar)
            } catch (e: BundleException) {
                when (e.type) {
                    // We log a warning if a bundle with the same symbolic name and version is already installed.
                    BundleException.DUPLICATE_BUNDLE_ERROR -> {
                        logger.warn("Bundle $jar has already been installed.", e)
                        null
                    }
                    else -> throw DriverInstallationException("Could not install $jar as a bundle.", e)
                }
            }
        }.forEach { bundle ->
            bundleUtils.startBundle(bundle)
        }
    }
}