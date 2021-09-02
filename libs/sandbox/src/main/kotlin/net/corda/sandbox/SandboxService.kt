package net.corda.sandbox

import net.corda.packaging.Cpk
import net.corda.v5.crypto.SecureHash
import java.util.UUID

/**
 * OSGi service interface for managing sandboxes.
 */
interface SandboxService {
    /**
     * Creates a new [SandboxGroup] containing a sandbox for each of the CPKs identified by the [cpkFileHashes].
     *
     * A [SandboxException] is thrown if the sandbox creation fails.
     */
    fun createSandboxes(cpkFileHashes: Iterable<SecureHash>): SandboxGroup

    /**
     * Creates a new [SandboxGroup] containing a sandbox for each of the CPKs identified by the [cpkFileHashes].
     *
     * A [SandboxException] is thrown if the sandbox creation fails.
     */
    fun createSandboxesWithoutStarting(cpkFileHashes: Iterable<SecureHash>): SandboxGroup

    /** Get the [Sandbox] with the given [id], or null if no match. */
    fun getSandbox(id: UUID): Sandbox?

    /**
     * Returns the [ClassInfo] for the given [klass].
     *
     * A [SandboxException] is thrown if the class is not in a sandbox, or is not found in any bundle the sandbox has
     * visibility of.
     */
    fun getClassInfo(klass: Class<*>): ClassInfo

    /**
     * Returns the [ClassInfo] for the class with the given [className]. If the className occurs more than once in
     * the sandboxGroup then the first one found is returned.
     *
     * A [SandboxException] is thrown if [className] is not found in the sandboxGroup.
     */
    fun getClassInfo(className: String): ClassInfo

    /**
     * Deletes the [Sandbox] with the given [id], and uninstall its bundles.
     *
     * A [SandboxException] is thrown if the sandbox does not exist, or its bundles cannot be uninstalled.
     */
    fun deleteSandbox(id: UUID)

    /**
     * Returns the [Sandbox] lowest in the stack of calls to this function, or null if no sandbox is on the stack.
     *
     * A [SandboxException] is thrown if the sandbox bundle's location is not formatted correctly, the ID is not a
     * valid UUID, or there is no known sandbox with the given ID.
     */
    fun getCallingSandbox(): Sandbox?

    /**
     * Finds the [Sandbox] lowest in the stack of calls to this function, and returns the [SandboxGroup] that contains
     * it. Returns null if no sandbox is on the stack.
     *
     * A [SandboxException] is thrown if the sandbox bundle's location is not formatted correctly, the ID is not a
     * valid UUID, there is no known sandbox with the given ID, or there is no sandbox group containing the sandbox
     * with the given ID.
     */
    fun getCallingSandboxGroup(): SandboxGroup?

    /**
     * Finds the [Sandbox] lowest in the stack of calls to this function, and returns the [Cpk.Identifier] of the
     * [Cpk] it was created from. Returns null if no sandbox is on the stack, or if the sandbox has no source CPK.
     *
     * A [SandboxException] is thrown if the sandbox bundle's location is not formatted correctly, the ID is not a
     * valid UUID, or there is no known sandbox with the given ID.
     */
    fun getCallingCpk(): Cpk.Identifier?
}