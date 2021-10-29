package net.corda.sandbox.internal

import net.corda.packaging.CPK
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
 * @param publicSandboxes An iterable containing all existing public sandboxes.
 */
internal class SandboxGroupImpl(
    private val bundleUtils: BundleUtils,
    private val sandboxesById: Map<CPK.Identifier, CpkSandboxInternal>,
    private val publicSandboxes: Iterable<SandboxInternal>,
    private val classTagFactory: ClassTagFactory
) : SandboxGroup {
    override val cpkSandboxes = sandboxesById.values

    override fun getSandbox(cpkIdentifier: CPK.Identifier) = sandboxesById[cpkIdentifier]
        ?: throw SandboxException("CPK $cpkIdentifier was not found in the sandbox group.")

    override fun loadClassFromCordappBundle(cpkIdentifier: CPK.Identifier, className: String) =
        getSandbox(cpkIdentifier).loadClassFromCordappBundle(className)

    override fun <T : Any> loadClassFromCordappBundle(className: String, type: Class<T>): Class<out T> {
        val containingSandbox = cpkSandboxes.find { sandbox -> sandbox.cordappBundleContainsClass(className) }
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

    override fun cordappClassCount(className: String) = cpkSandboxes.count { sandbox ->
        sandbox.cordappBundleContainsClass(className)
    }

    override fun getStaticTag(klass: Class<*>) = getClassTag(klass, isStaticTag = true)

    override fun getEvolvableTag(klass: Class<*>) = getClassTag(klass, isStaticTag = false)

    override fun getClass(className: String, serialisedClassTag: String): Class<*> {
        val classTag = classTagFactory.deserialise(serialisedClassTag)

        if (classTag.isCpkClass) {
            val sandbox = when (classTag) {
                is StaticTag -> cpkSandboxes.find { sandbox -> sandbox.cpk.metadata.hash == classTag.cpkFileHash }
                is EvolvableTag -> cpkSandboxes.find { sandbox ->
                    sandbox.cpk.metadata.id.signerSummaryHash == classTag.cpkSignerSummaryHash
                            && sandbox.cordappBundle.symbolicName == classTag.cordappBundleName
                }
            } ?: throw SandboxException(
                "CPK class tag for $className did not match any CPK sandbox in the sandbox group."
            )

            return sandbox.loadClass(className, classTag.classBundleName) ?: throw SandboxException(
                "Class $className could not be loaded from bundle ${classTag.classBundleName} in sandbox ${sandbox.id}."
            )

        } else {
            val bundle = bundleUtils.allBundles.find { bundle -> bundle.symbolicName == classTag.classBundleName }
                ?: throw SandboxException("Class tag for $className did not match any loaded bundle.")
            return try {
                bundle.loadClass(className)
            } catch (e: ClassNotFoundException) {
                throw SandboxException(
                    "Class $className could not be loaded from bundle ${bundle.symbolicName}."
                )
            }
        }
    }

    /**
     * Returns the serialised `ClassTag` for a given [klass].
     *
     * If [isStaticTag] is true, a serialised [StaticTag] is returned. Otherwise, a serialised [EvolvableTag] is
     * returned.
     *
     * Throws [SandboxException] if the class is not contained in any bundle, or is contained in a bundle that does not
     * have a symbolic name.
     */
    // TODO - CORE-2884: Add handling of non-bundle and Felix framework classes.
    private fun getClassTag(klass: Class<*>, isStaticTag: Boolean): String {
        val bundle = bundleUtils.getBundle(klass)
            ?: throw SandboxException("Class ${klass.name} was not loaded from any bundle.")

        val cpkSandbox = cpkSandboxes.find { sandbox -> sandbox.containsBundle(bundle) }
        if (cpkSandbox != null) {
            return classTagFactory.createSerialised(isStaticTag, bundle, cpkSandbox)
        }

        return classTagFactory.createSerialised(isStaticTag, bundle, null)
    }
}