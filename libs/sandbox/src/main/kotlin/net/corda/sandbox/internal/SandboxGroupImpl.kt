package net.corda.sandbox.internal

import net.corda.sandbox.SandboxException
import net.corda.sandbox.SandboxGroup
import net.corda.sandbox.internal.classtag.ClassTagFactory
import net.corda.sandbox.internal.classtag.EvolvableTag
import net.corda.sandbox.internal.classtag.StaticTag
import net.corda.sandbox.internal.sandbox.CpkSandbox
import net.corda.sandbox.internal.sandbox.Sandbox
import net.corda.sandbox.internal.utilities.BundleUtils

/**
 * An implementation of the [SandboxGroup] interface.
 *
 * @param bundleUtils The utils that all OSGi activity is delegated to for testing purposes.
 * @param sandboxes The CPK sandboxes in this sandbox group.
 * @param publicSandboxes An iterable containing all existing public sandboxes.
 */
internal class SandboxGroupImpl(
    override val sandboxes: Collection<CpkSandbox>,
    private val publicSandboxes: Iterable<Sandbox>,
    private val classTagFactory: ClassTagFactory,
    private val bundleUtils: BundleUtils
) : SandboxGroupInternal {
    override fun <T : Any> loadClassFromMainBundles(className: String, type: Class<T>): Class<out T> {
        val klass = sandboxes.mapNotNull { sandbox ->
            try {
                sandbox.loadClassFromMainBundle(className)
            } catch (e: SandboxException) {
                null
            }
        }.firstOrNull()
            ?: throw SandboxException("Class $className was not found in any sandbox in the sandbox group.")

        return try {
            klass.asSubclass(type)
        } catch (e: ClassCastException) {
            throw SandboxException(
                "Class $className was found in sandbox, but was not of the provided type $type."
            )
        }
    }

    override fun getStaticTag(klass: Class<*>) = getClassTag(klass, isStaticTag = true)

    override fun getEvolvableTag(klass: Class<*>) = getClassTag(klass, isStaticTag = false)

    override fun getClass(className: String, serialisedClassTag: String): Class<*> {
        val classTag = classTagFactory.deserialise(serialisedClassTag)

        if (!classTag.isPublicClass) {
            val sandbox = when (classTag) {
                is StaticTag -> sandboxes.find { sandbox -> sandbox.cpk.metadata.hash == classTag.cpkFileHash }
                is EvolvableTag -> sandboxes.find { sandbox ->
                    sandbox.cpk.metadata.id.signerSummaryHash == classTag.cpkSignerSummaryHash
                            && sandbox.mainBundle.symbolicName == classTag.mainBundleName
                }
            } ?: throw SandboxException(
                "Class tag $className did not match any sandbox in the sandbox group or a public sandboxe."
            )
            return sandbox.loadClass(className, classTag.classBundleName) ?: throw SandboxException(
                "Class $className could not be loaded from bundle ${classTag.classBundleName} in sandbox ${sandbox.id}."
            )

        } else {
            publicSandboxes.forEach { publicSandbox ->
                val klass = publicSandbox.loadClass(className, classTag.classBundleName)
                if (klass != null) return klass
            }
            throw SandboxException(
                "Class $className from bundle ${classTag.classBundleName} could not be loaded from any of the public " +
                        "sandboxes."
            )
        }
    }

    /**
     * Returns the serialised `ClassTag` for a given [klass].
     *
     * If [isStaticTag] is true, a serialised [StaticTag] is returned. Otherwise, a serialised [EvolvableTag] is
     * returned.
     *
     * Throws [SandboxException] if the class is not contained in any bundle, the class is contained in a bundle that
     * is not contained in any sandbox in the group or in a public sandbox, or the class is contained in a bundle
     * that does not have a symbolic name.
     */
    private fun getClassTag(klass: Class<*>, isStaticTag: Boolean): String {
        val bundle = bundleUtils.getBundle(klass)
            ?: throw SandboxException("Class ${klass.name} was not loaded from any bundle.")

        val publicSandbox = publicSandboxes.find { sandbox -> sandbox.containsBundle(bundle) }

        return if (publicSandbox != null) {
            classTagFactory.createSerialised(isStaticTag, true, bundle, publicSandbox)
        } else {
            val sandbox = sandboxes.find { sandbox -> sandbox.containsBundle(bundle) } ?: throw SandboxException(
                "Bundle ${bundle.symbolicName} was not found in the sandbox group or in a public sandbox."
            )

            classTagFactory.createSerialised(isStaticTag, false, bundle, sandbox)
        }
    }
}