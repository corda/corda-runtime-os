package net.corda.sandbox.internal.classtag

import net.corda.sandbox.internal.sandbox.CpkSandbox
import org.osgi.framework.Bundle

/** Creates, serialises and deserialises [ClassTag] objects. */
internal interface ClassTagFactory {
    /**
     * Creates and serialises a [StaticTag].
     *
     * Throws `SandboxException` if the [bundle] does not have a symbolic name.
     *
     * @param bundle The bundle the class is loaded from.
     * @param cpkSandbox The CPK sandbox the class is loaded from, or null if the class is not from a CPK sandbox.
     */
    fun createSerialisedStaticTag(bundle: Bundle, cpkSandbox: CpkSandbox?): String

    /**
     * Creates and serialises an [EvolvableTag].
     *
     * Throws `SandboxException` if the [bundle] does not have a symbolic name.
     *
     * @param bundle The bundle the class is loaded from.
     * @param cpkSandbox The CPK sandbox the class is loaded from, or null if the class is not from a CPK sandbox.
     */
    fun createSerialisedEvolvableTag(bundle: Bundle, cpkSandbox: CpkSandbox?): String

    /**
     * Deserialises a [ClassTag].
     *
     * Throws `SandboxException` if the [serialisedClassTag] cannot be deserialised.
     */
    fun deserialise(serialisedClassTag: String): ClassTag
}