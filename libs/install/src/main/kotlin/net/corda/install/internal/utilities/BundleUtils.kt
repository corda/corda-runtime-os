package net.corda.install.internal.utilities

import net.corda.install.InstallService
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.BundleException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.net.URI

/** Handles bundle operations for the various [InstallService] components. */
@Component(service = [BundleUtils::class])
internal class BundleUtils @Activate constructor(private val bundleContext: BundleContext) {
    /**
     * Installs a bundle from the location specified by the [uri].
     *
     * A [BundleException] is thrown if the bundle fails to install. */
    fun installAsBundle(uri: URI): Bundle = bundleContext.installBundle(uri.toString())

    /**
     * Starts the [bundle].
     *
     * A [BundleException] is thrown if the bundle fails to start. */
    fun startBundle(bundle: Bundle) = bundle.start()
}