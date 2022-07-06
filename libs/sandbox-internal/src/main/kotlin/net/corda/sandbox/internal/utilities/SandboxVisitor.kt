package net.corda.sandbox.internal.utilities.dot

import net.corda.sandbox.internal.sandbox.Sandbox
import org.osgi.framework.Bundle
import java.util.UUID


/**
 * Trivial visitor - we visit a sandbox, which may be a regular
 * sandbox or a cpk sandbox, and then visit private and public OSGi bundles.
 */
internal interface SandboxVisitor {
    /** Using types to discriminate between public and private bundles and their sandboxes */
    data class PrivateBundle(val thisSandboxId: UUID, val bundle: Bundle)

    /** Using types to discriminate between public and private bundles and their sandboxes */
    data class PublicBundle(val thisSandboxId: UUID, val bundle: Bundle)

    fun visit(sandbox: Sandbox)
    fun visit(bundle: PrivateBundle)
    fun visit(bundle: PublicBundle)

    fun complete()
}
