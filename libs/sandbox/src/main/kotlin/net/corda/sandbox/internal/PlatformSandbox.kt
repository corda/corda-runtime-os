package net.corda.sandbox.internal

import net.corda.sandbox.Sandbox
import net.corda.sandbox.internal.utilities.BundleUtils
import org.osgi.framework.Bundle
import java.util.UUID

/** A [Sandbox] created from platform bundles, rather than created from a CPK. */
internal class PlatformSandbox(
    bundleUtils: BundleUtils,
    id: UUID,
    bundles: Set<Bundle>
) : SandboxInternalAbstractImpl(bundleUtils, id, bundles) {

    override fun isCordappBundle(bundle: Bundle) = false
}
