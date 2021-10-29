package net.corda.sandbox.internal.classtag

import net.corda.sandbox.internal.sandbox.CpkSandbox
import org.osgi.framework.Bundle

/** Creates, serialises and deserialises [ClassTag] objects. */
internal interface ClassTagFactory {
    // TODO - Update.
    /**
     * Creates and serialises a [ClassTag].
     *
     * Throws `SandboxException` if the [bundle] does not have a symbolic name.
     *
     * @param isStaticClassTag Indicates whether to create a [StaticTag] or an [EvolvableTag].
     * @param bundle The bundle the class is loaded from.
     * @param cpkSandbox The CPK sandbox the class is loaded from, or null if the class is not from a CPK sandbox.
     */
    fun createSerialisedStaticTag(bundle: Bundle, cpkSandbox: CpkSandbox?): String

    // TODO - Describe.
    fun createSerialisedEvolvableTag(bundle: Bundle, cpkSandbox: CpkSandbox?): String

    /**
     * Deserialises a [ClassTag].
     *
     * Throws `SandboxException` if the [serialisedClassTag] cannot be deserialised.
     */
    fun deserialise(serialisedClassTag: String): ClassTag
}