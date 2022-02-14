package net.corda.testing.sandboxes

import java.nio.file.Path
import org.osgi.framework.BundleContext

interface SandboxSetup {
    fun configure(
        bundleContext: BundleContext,
        baseDirectory: Path,
        extraPublicBundleNames: Set<String> = emptySet()
    )
    fun shutdown()
}
