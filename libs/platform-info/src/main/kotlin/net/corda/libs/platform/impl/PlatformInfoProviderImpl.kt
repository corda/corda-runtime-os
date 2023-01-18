package net.corda.libs.platform.impl

import net.corda.libs.platform.PlatformInfoProvider
import org.osgi.framework.BundleContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component


@Component(service = [PlatformInfoProvider::class])
class PlatformInfoProviderImpl @Activate constructor(
    bundleContext: BundleContext,
) : PlatformInfoProvider {

    internal companion object {
        const val STUB_PLATFORM_VERSION = 5000

        private const val DEFAULT_PLATFORM_VERSION = 5000
        private const val PLATFORM_VERSION_KEY = "net.corda.platform.version"
    }

    /** Temporary stub values **/
    override val activePlatformVersion = STUB_PLATFORM_VERSION

    override val localWorkerPlatformVersion = bundleContext.getProperty(PLATFORM_VERSION_KEY)?.toInt() ?: DEFAULT_PLATFORM_VERSION

    override val localWorkerSoftwareVersion = bundleContext.bundle.version.toString()
}