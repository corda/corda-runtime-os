package net.corda.sandbox

import java.util.UUID
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.framework.Bundle

/**
 * A group of sandboxes with visibility of one another.
 *
 * @property id A unique identifier for this [SandboxGroup].
 * @property metadata [CpkMetadata] for each CPK's "main" bundle.
 */
interface SandboxGroup: SingletonSerializeAsToken {
    val id: UUID

    val metadata: Map<Bundle, CpkMetadata>

    /**
     * Attempts to load the [Class] with [className] from the main bundle of each sandbox in the
     * sandbox group in turn. Can only find classes belonging to exported packages.
     *
     * @throws [SandboxException] if no sandbox contains the named class, multiple sandboxes contain the named class,
     * or if any of the sandboxes' main bundles are uninstalled.
     */
    fun loadClassFromMainBundles(className: String): Class<*>

    /**
     * Attempts to load the [Class] with [className] from the main bundle of each sandbox in the sandbox group in
     * turn. Casts the first match to type [T] and returns it. Can only find classes belonging to exported packages.
     *
     * @throws [SandboxException] if no sandbox contains the named class, multiple sandboxes contain the named class,
     * if any of the sandboxes' main bundles are uninstalled, or if the named class is not of the correct type.
     */
    fun <T : Any> loadClassFromMainBundles(className: String, type: Class<T>): Class<out T>

    /**
     * Returns the serialised static tag for a given [klass].
     *
     * @throws [SandboxException] if the class is not loaded from any bundle, or if the class is contained in a bundle
     * that does not have a symbolic name.
     */
    fun getStaticTag(klass: Class<*>): String

    /**
     * Returns the serialised evolvable tag for a given [klass].
     *
     * @throws [SandboxException] if the class is not loaded from any bundle, if the class is contained in a bundle
     * that does not have a symbolic name, or if an [net.corda.sandbox.internal.classtag.EvolvableTag] is requested
     * for class defined in a CPK's private bundle.
     */
    fun getEvolvableTag(klass: Class<*>): String

    /**
     * Returns the [Class] identified by the [className] and the [serialisedClassTag].
     *
     * @throws [SandboxException] if there is no sandbox matching the tag, if the class is not contained in the matching
     * sandbox or in a public sandbox, if the class tag cannot be parsed, or if attempted to load a class with an
     * [net.corda.sandbox.internal.classtag.EvolvableTag] from a CPK private bundle.
     */
    fun getClass(className: String, serialisedClassTag: String): Class<*>
    fun loadClassFromPublicBundles(className: String): Class<*>?
}
