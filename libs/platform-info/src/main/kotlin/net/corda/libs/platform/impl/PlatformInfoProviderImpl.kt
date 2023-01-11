package net.corda.libs.platform.impl

import net.corda.libs.platform.PlatformInfoProvider
import net.corda.v5.base.util.contextLogger
import org.osgi.framework.BundleContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.util.jar.Manifest

@Component(service = [PlatformInfoProvider::class])
class PlatformInfoProviderImpl internal constructor(
    private val classLoader: ClassLoader,
    private val bundleContext: BundleContext
) : PlatformInfoProvider {

    @Activate
    constructor(bundleContext: BundleContext) : this(PlatformInfoProvider::class.java.classLoader, bundleContext)

    internal companion object {
        val logger = contextLogger()

        const val STUB_PLATFORM_VERSION = 5000

        const val DEFAULT_SOFTWARE_VERSION = "5.0.0.0-SNAPSHOT"
        const val MANIFEST_FILE_NAME = "META-INF/MANIFEST.MF"
        const val BUNDLE_VERSION = "Bundle-Version"
    }

    /** Temporary stub values **/
    override val activePlatformVersion = STUB_PLATFORM_VERSION

    override val localWorkerPlatformVersion by lazy {
        bundleContext.getProperty("net.corda.platform.version").toInt()
    }

    override val localWorkerSoftwareVersion by lazy {
        bundleManifest?.mainAttributes?.getValue(BUNDLE_VERSION)
            ?: DEFAULT_SOFTWARE_VERSION.also {
                logger.warn("Unable to retrieve software version from the Manifest file. Defaulting to \"$it\"")
            }
    }

    private val bundleManifest by lazy {
        classLoader
            .getResources(MANIFEST_FILE_NAME)
            .asSequence()
            .map {
                it.openStream().use { input ->
                    Manifest(input)
                }
            }.filter {
                it.mainAttributes.getValue("Bundle-SymbolicName") == "net.corda.platform-info"
            }.firstOrNull()
    }
}