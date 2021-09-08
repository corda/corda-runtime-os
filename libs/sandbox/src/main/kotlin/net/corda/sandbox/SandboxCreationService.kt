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
     * The bundles in each sandbox are not started, meaning that their bundle activators are not called. This is used
     * when the CPKs' classes are required (e.g. to retrieve the CorDapp schemas), but we do not want to run any
     * activation logic.
     *
     * A [SandboxException] is thrown if the sandbox creation fails.
     */
    fun createSandboxesWithoutStarting(cpkFileHashes: Iterable<SecureHash>): SandboxGroup
}