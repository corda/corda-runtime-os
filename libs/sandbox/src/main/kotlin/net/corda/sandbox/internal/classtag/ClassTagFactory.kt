package net.corda.sandbox.internal.classtag

import net.corda.sandbox.internal.sandbox.CpkSandboxInternal
import org.osgi.framework.Bundle

/** Creates, serialises and deserialises [ClassTag] objects. */
internal interface ClassTagFactory {
    /**
     * Creates and serialises a [ClassTag].
     *
     * Throws `SandboxException` if the [bundle] does not have a symbolic name.
     *
     * @param isStaticClassTag Whether to create a static or an evolvable class tag.
     * @param bundle The bundle the class is loaded from.
     * @param sandbox The CPK sandbox the class is loaded from, or null if the class is not loaded from a CPK.
     */
    fun createSerialised(
        isStaticClassTag: Boolean,
        bundle: Bundle,
        sandbox: CpkSandboxInternal?
    ): String

    /**
     * Deserialises a [ClassTag].
     *
     * Throws `SandboxException` if the [serialisedClassTag] cannot be deserialised.
     */
    fun deserialise(serialisedClassTag: String): ClassTag
}