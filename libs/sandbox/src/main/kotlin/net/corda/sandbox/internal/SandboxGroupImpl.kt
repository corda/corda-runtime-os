package net.corda.sandbox.internal

import net.corda.packaging.Cpk
import net.corda.sandbox.SandboxException
import net.corda.sandbox.SandboxGroup
import net.corda.sandbox.internal.classtag.ClassTagFactory
import net.corda.sandbox.internal.classtag.EvolvableTag
import net.corda.sandbox.internal.classtag.StaticTag
import net.corda.sandbox.internal.sandbox.CpkSandboxInternal
import net.corda.sandbox.internal.sandbox.SandboxInternal
import net.corda.sandbox.internal.utilities.BundleUtils

/**
 * An implementation of the [SandboxGroup] interface.
 *
 * @param bundleUtils The [BundleUtils] that all OSGi activity is delegated to for testing purposes.
 * @param sandboxesById The [CpkSandboxInternal]s in this sandbox group, keyed by the identifier of their CPK.
 * @param nonCpkSandbox The sandbox containing all the non-CPK bundles.
 */
internal class SandboxGroupImpl(
    private val bundleUtils: BundleUtils,
    private val sandboxesById: Map<Cpk.Identifier, CpkSandboxInternal>,
    private val nonCpkSandbox: SandboxInternal,
    private val classTagFactory: ClassTagFactory
) : SandboxGroup {
    override val sandboxes = sandboxesById.values

    override fun getSandbox(cpkIdentifier: Cpk.Identifier) = sandboxesById[cpkIdentifier]
        ?: throw SandboxException("CPK $cpkIdentifier was not found in the sandbox group.")

    override fun loadClassFromCordappBundle(cpkIdentifier: Cpk.Identifier, className: String) =
        getSandbox(cpkIdentifier).loadClassFromCordappBundle(className)

    override fun <T : Any> loadClassFromCordappBundle(className: String, type: Class<T>): Class<out T> {
        val containingSandbox = sandboxes.find { sandbox -> sandbox.cordappBundleContainsClass(className) }
            ?: throw SandboxException("Class $className was not found in any sandbox in the sandbox group.")
        val klass = containingSandbox.loadClassFromCordappBundle(className)

        return try {
            klass.asSubclass(type)
        } catch (e: ClassCastException) {
            throw SandboxException(
                "Class $className was found in sandbox, but was not of the provided type $type."
            )
        }
    }

    override fun cordappClassCount(className: String) = sandboxes.count { sandbox ->
        sandbox.cordappBundleContainsClass(className)
    }

    override fun  getStaticTag(klass: Class<*>) = getClassTag(klass, isStaticTag = true)

    override fun getEvolvableTag(klass: Class<*>) = getClassTag(klass, isStaticTag = false)

    override fun getClass(className: String, serialisedClassTag: String): Class<*> {
        val classTag = classTagFactory.deserialise(serialisedClassTag)

        val sandbox = if (classTag.isNonCpkClass) {
            nonCpkSandbox
        } else {
            when (classTag) {
                is StaticTag -> sandboxes.find { sandbox -> sandbox.cpk.cpkHash == classTag.cpkFileHash }
                is EvolvableTag -> sandboxes.find { sandbox ->
                    sandbox.cpk.id.signers == classTag.cpkPublicKeyHashes
                            && sandbox.cordappBundle.symbolicName == classTag.cordappBundleName
                }
            }
        } ?: throw SandboxException(
            "Class tag $className did not match any sandbox in the sandbox group or the non-CPK sandbox."
        )

        return sandbox.loadClass(className, classTag.classBundleName) ?: throw SandboxException(
            "Class $className could not be loaded from bundle ${classTag.classBundleName} in sandbox ${sandbox.id}."
        )
    }

    /**
     * Returns the serialised `ClassTag` for a given [klass].
     *
     * If [isStaticTag] is true, a serialised [StaticTag] is returned. Otherwise, a serialised [EvolvableTag] is
     * returned.
     *
     * Throws [SandboxException] if the class is not contained in any bundle, the class is contained in a bundle that
     * is not contained in any sandbox in the group or in the non-CPK sandbox, or the class is contained in a bundle
     * that does not have a symbolic name.
     */
    private fun getClassTag(klass: Class<*>, isStaticTag: Boolean): String {
        val bundle = bundleUtils.getBundle(klass)
            ?: throw SandboxException("Class ${klass.name} was not loaded from any bundle.")

        val sandbox = (sandboxes + nonCpkSandbox).find { sandbox -> sandbox.containsBundle(bundle) }
            ?: throw SandboxException(
                "Bundle ${bundle.symbolicName} was not found in the sandbox group or in the non-CPK sandbox."
            )

        val isNonCpkBundle = nonCpkSandbox.containsBundle(bundle)

        return classTagFactory.createSerialised(isStaticTag, isNonCpkBundle, bundle, sandbox)
    }
}