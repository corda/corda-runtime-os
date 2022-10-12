package net.corda.libs.platform

/**
 * Service for retrieving corda platform information for the current Corda cluster the service is running on.
 */
interface PlatformInfoProvider {

    /**
     * The platform version of the current Corda cluster.
     */
    val platformVersion: Int
}