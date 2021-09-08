package net.corda.sandbox

import net.corda.v5.crypto.SecureHash

/**
 * OSGi service interface for creating sandboxes.
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
     * The bundles in each sandbox are not started, meaning that their bundle activators are not called.
     *
     * A [SandboxException] is thrown if the sandbox creation fails.
     */
    fun createSandboxesWithoutStarting(cpkFileHashes: Iterable<SecureHash>): SandboxGroup
}