package net.corda.sandbox

import net.corda.v5.crypto.SecureHash
import org.osgi.framework.Bundle

/**
 * A service for creating sandboxes. There are two types of sandbox:
 *
 * * Public sandboxes have visibility of, and are visible to, all other sandboxes
 * * CPK sandboxes are created from previously-installed CPKs
 */
interface SandboxCreationService {
    /**
     * Creates a new public sandbox.
     */
    fun createPublicSandbox(publicBundles: Iterable<Bundle>, privateBundles: Iterable<Bundle>)

    /**
     * Creates a new [SandboxGroup] containing a sandbox for each of the CPKs identified by the [cpkFileHashes].
     * Duplicate [cpkFileHashes] are discarded (i.e. if two hashes are identical, only one sandbox will be created).
     *
     * A [SandboxException] is thrown if the sandbox creation fails.
     */
    fun createSandboxGroup(cpkFileHashes: Iterable<SecureHash>): SandboxGroup

    /**
     * Creates a new [SandboxGroup] containing a sandbox for each of the CPKs identified by the [cpkFileHashes].
     * Duplicate [cpkFileHashes] are discarded (i.e. if two hashes are identical, only one sandbox will be created).
     *
     * The bundles in each sandbox are not started, meaning that their bundle activators are not called. This is used
     * when the CPKs' classes are required (e.g. to retrieve the CorDapp schemas), but we do not want to run any
     * activation logic.
     *
     * A [SandboxException] is thrown if the sandbox creation fails.
     */
    fun createSandboxGroupWithoutStarting(cpkFileHashes: Iterable<SecureHash>): SandboxGroup
}