package net.corda.sandbox

import net.corda.v5.crypto.SecureHash

/**
 * A service for creating sandboxes.
 *
 * The service is initialised with a single [Sandbox], the platform sandbox. This sandbox contains the public and
 * private bundles named by the `PLATFORM_SANDBOX_PUBLIC_BUNDLES_KEY` and `PLATFORM_SANDBOX_PRIVATE_BUNDLES_KEY`
 * configuration admin properties. This sandbox is initialised lazily; the named bundles must be installed and the
 * configuration admin properties set before this service is first interacted with.
 *
 * Sandboxes thereafter are created from CPKs. Every CPK sandbox is granted visibility of the platform sandbox.
 *
 * The platform sandbox receives special treatment in terms of visibility; its public bundles have visibility of even
 * private bundles in the CPK sandboxes.
 */
interface SandboxCreationService {
    /**
     * Creates a new [SandboxGroup] containing a sandbox for each of the CPKs identified by the [cpkFileHashes].
     * Duplicate [cpkFileHashes] are discarded (i.e. if two hashes are identical, only one sandbox will be created).
     *
     * A [SandboxException] is thrown if the sandbox creation fails.
     */
    fun createSandboxes(cpkFileHashes: Iterable<SecureHash>): SandboxGroup

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
    fun createSandboxesWithoutStarting(cpkFileHashes: Iterable<SecureHash>): SandboxGroup
}