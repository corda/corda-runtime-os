package net.corda.sandbox

import net.corda.libs.packaging.Cpk
import org.osgi.framework.Bundle

/**
 * A service for creating sandboxes. There are two types of sandbox:
 *
 * * Public sandboxes have visibility of, and are visible to, all other sandboxes
 * * CPK sandboxes are created from previously-installed CPKs
 */
interface SandboxCreationService {
    /** Creates a new public sandbox, containing the given [publicBundles] and [privateBundles]. */
    fun createPublicSandbox(publicBundles: Iterable<Bundle>, privateBundles: Iterable<Bundle>)

    /**
     * Creates a new [SandboxGroup] in the [securityDomain] containing a sandbox for each of the [cpks].
     *
     * Duplicate [cpks] are discarded (i.e. only one sandbox will be created per unique hash).
     *
     * A [SandboxException] is thrown if the [securityDomain] contains a '/' character, or if sandbox creation fails.
     */
    fun createSandboxGroup(cpks: Iterable<Cpk>, securityDomain: String = ""): SandboxGroup

    /**
     * Creates a new [SandboxGroup] in the [securityDomain] containing a sandbox for each of the [cpks].
     *
     * Duplicate [cpks] are discarded (i.e. only one sandbox will be created per unique hash).
     *
     * The bundles in each sandbox are not started, meaning that their bundle activators are not called.
     *
     * A [SandboxException] is thrown if the sandbox creation fails.
     */
    fun createSandboxGroupWithoutStarting(cpks: Iterable<Cpk>, securityDomain: String = ""): SandboxGroup

    /**
     * Attempts to uninstall each of the sandbox group's bundles in turn, and removes the sandbox group from the
     * service's cache.
     */
    fun unloadSandboxGroup(sandboxGroup: SandboxGroup)
}