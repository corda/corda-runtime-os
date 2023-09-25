package net.corda.libs.platform

/**
 * For retrieving corda platform information for the current Corda cluster this service is running on.
 */
interface PlatformInfoProvider {

    /**
     * The platform version of the current Corda cluster.
     * This is sourced from the cluster configuration.
     */
    val activePlatformVersion: Int

    /**
     * The platform version of the current Corda cluster worker.
     * This is sourced from the installed JAR's manifest.
     */
    val localWorkerPlatformVersion: Int

    /**
     * The software version of the current Corda cluster worker.
     * This is sourced from `Bundle-Version` in the installed JAR's manifest.
     */
    val localWorkerSoftwareVersion: String

    val localWorkerSoftwareShortVersion: String
        get() {
            val versionParts = localWorkerSoftwareVersion.split(".")
            check(versionParts.count() >= 2) { "Version has to have at least 2 parts." }
            return versionParts.take(2).joinToString(".")
        }
}