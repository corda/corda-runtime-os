package net.corda.sandbox

import net.corda.packaging.Cpb
import net.corda.packaging.Cpk
import net.corda.v5.crypto.SecureHash
import java.util.UUID

/**
 * OSGi service interface for managing sandboxes.
 */
interface SandboxService {
    /**
     * Creates a new [Sandbox] for each CPK in the CPB identified by [cpbIdentifier].
     *
     * A [SandboxException] is thrown if the sandbox creation fails.
     */
    fun createSandboxes(cpbIdentifier: Cpb.Identifier): SandboxGroup

    /**
     * Creates a new [Sandbox] for the each CPK in the CPB identified by [cpbIdentifier].
     *
     * A [SandboxException] is thrown if the sandbox creation fails.
     */
    fun createSandboxesWithoutStarting(cpbIdentifier: Cpb.Identifier): SandboxGroup

    /**
     * Creates a new [Sandbox] for each of the CPKs identified by the [cpkIdentifiers].
     *
     * A [SandboxException] is thrown if the sandbox creation fails.
     */
    fun createSandboxesFromIdentifiers(cpkIdentifiers: Iterable<Cpk.Identifier>): SandboxGroup

    /**
     * Creates a new [Sandbox] for each of the CPKs identified by the [cpkHashes].
     *
     * A [SandboxException] is thrown if the sandbox creation fails.
     */
    fun createSandboxesFromHashes(cpkHashes: Iterable<SecureHash>): SandboxGroup

    /**
     * Creates a new [Sandbox] for each of the CPKs identified by the [cpkHashes].
     *
     * A [SandboxException] is thrown if the sandbox creation fails.
     */
    fun createSandboxesFromHashesWithoutStarting(cpkHashes: Iterable<SecureHash>): SandboxGroup

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
     * Delete the [Sandbox] with the given [id], and uninstall its bundles.
     *
     * A [SandboxException] is thrown if the sandbox does not exist, or its bundles cannot be uninstalled.
     */
    fun deleteSandbox(id: UUID)
}