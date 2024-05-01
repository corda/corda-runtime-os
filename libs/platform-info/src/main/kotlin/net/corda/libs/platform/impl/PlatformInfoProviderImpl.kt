package net.corda.libs.platform.impl

import net.corda.libs.platform.PlatformInfoProvider
import net.corda.libs.platform.PlatformVersion.CORDA_5_2
import net.corda.libs.platform.PlatformVersion.CORDA_JSON_BLOB_HEADER
import org.osgi.framework.BundleContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component


@Component(service = [PlatformInfoProvider::class])
class PlatformInfoProviderImpl @Activate constructor(
    bundleContext: BundleContext,
) : PlatformInfoProvider {

    internal companion object {
        val STUB_PLATFORM_VERSION = CORDA_JSON_BLOB_HEADER.value

        private val DEFAULT_PLATFORM_VERSION = CORDA_5_2.value
        private const val PLATFORM_VERSION_KEY = "net.corda.platform.version"
    }

    /** Temporary stub values **/
    override val activePlatformVersion = STUB_PLATFORM_VERSION

    override val localWorkerPlatformVersion = bundleContext.getProperty(PLATFORM_VERSION_KEY)?.toInt() ?: DEFAULT_PLATFORM_VERSION

    override val localWorkerSoftwareVersion = bundleContext.bundle.version.toString()
}