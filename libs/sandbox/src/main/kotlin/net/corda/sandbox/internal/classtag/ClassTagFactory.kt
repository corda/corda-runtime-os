package net.corda.sandbox.internal.classtag

import net.corda.sandbox.Sandbox
import org.osgi.framework.Bundle

/** Creates, serialises and deserialises [ClassTag] objects. */
internal interface ClassTagFactory {
    /**
     * Creates and serialises a [ClassTag].
     *
     * Throws `SandboxException` if the [bundle] does not have a symbolic name, or [sandbox] is neither the non-CPK
     * sandbox nor a CPK sandbox.
     */
    fun createSerialised(
        isStaticClassTag: Boolean,
        isNonCpkBundle: Boolean,
        bundle: Bundle,
        sandbox: Sandbox
    ): String

    /**
     * Deserialises a [ClassTag].
     *
     * Throws `SandboxException` if the [serialisedClassTag] cannot be deserialised.
     */
    fun deserialise(serialisedClassTag: String): ClassTag
}