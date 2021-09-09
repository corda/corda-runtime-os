package net.corda.sandbox.internal

import net.corda.packaging.Cpk
import net.corda.sandbox.ClassTag
import net.corda.sandbox.EvolvableTag
import net.corda.sandbox.SandboxException
import net.corda.sandbox.SandboxGroup
import net.corda.sandbox.StaticTag
import net.corda.sandbox.internal.sandbox.CpkSandboxInternal
import net.corda.sandbox.internal.sandbox.SandboxInternal
import net.corda.sandbox.internal.utilities.BundleUtils
import net.corda.v5.crypto.SecureHash
import java.util.Collections

/**
 * An implementation of the [SandboxGroup] interface.
 *
 * @param bundleUtils The [BundleUtils] that all OSGi activity is delegated to for testing purposes
 * @param sandboxesById The [CpkSandboxInternal]s in this sandbox group, keyed by the identifier of their CPK
 * @param platformSandbox The sandbox containing all the platform (i.e. non CPK) bundles
 */
internal class SandboxGroupImpl(
    private val bundleUtils: BundleUtils,
    private val sandboxesById: Map<Cpk.Identifier, CpkSandboxInternal>,
    private val platformSandbox: SandboxInternal
) : SandboxGroup {

    companion object {
        // Used as placeholders when generating class tags for platform classes. However, it is not safe to use these
        // to determine whether a given tag corresponds to a platform bundle. The `ClassTag.isPlatformClass` property
        // should be used instead.
        private const val PLACEHOLDER_CORDAPP_BUNDLE_NAME = "PLATFORM_BUNDLE"
        private val PLACEHOLDER_CPK_FILE_HASH = SecureHash.create("SHA-256:0000000000000000")
        private val PLACEHOLDER_CPK_PUBLIC_KEY_HASHES = Collections.emptyNavigableSet<SecureHash>()
    }

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

    override fun getStaticTag(klass: Class<*>) = getClassTag(klass, isStaticTag = true) as StaticTag

    override fun getEvolvableTag(klass: Class<*>) = getClassTag(klass, isStaticTag = false) as EvolvableTag

    override fun getClass(className: String, classTag: ClassTag): Class<*> {
        val sandbox = if (classTag.isPlatformClass) {
            platformSandbox
        } else {
            when (classTag) {
                is StaticTag -> sandboxes.find { sandbox -> sandbox.cpk.cpkHash == classTag.cpkFileHash }
                is EvolvableTag -> sandboxes.find { sandbox ->
                    sandbox.cpk.id.signers == classTag.cpkPublicKeyHashes
                            && sandbox.cordappBundle.symbolicName == classTag.cordappBundleName
                }
                else -> throw SandboxException("Unrecognised class tag type ${classTag::class.java.name}.")
            }
        } ?: throw SandboxException(
            "Class tag $className did not match any sandbox in the sandbox group or the " +
                    "platform sandbox."
        )

        return sandbox.loadClass(className, classTag.classBundleName) ?: throw SandboxException(
            "Class $className could not be loaded from bundle ${classTag.classBundleName} in sandbox ${sandbox.id}."
        )
    }

    /**
     * Returns the [ClassTag] for a given [klass].
     *
     * If [isStaticTag] is true, a [StaticTag] is returned. Otherwise, an [EvolvableTag] is returned.
     *
     * Throws [SandboxException] if the class is not contained in any bundle, or is contained in a bundle that is not
     * contained in any sandbox in the group or in the platform sandbox.
     */
    private fun getClassTag(klass: Class<*>, isStaticTag: Boolean): ClassTag {
        val bundle = bundleUtils.getBundle(klass) ?: throw SandboxException(
            "Class ${klass.name} was not loaded from any bundle."
        )

        if (platformSandbox.containsBundle(bundle)) {
            return if (isStaticTag) {
                StaticTag(PLACEHOLDER_CPK_FILE_HASH, isPlatformClass = true, bundle.symbolicName)
            } else {
                EvolvableTag(
                    PLACEHOLDER_CORDAPP_BUNDLE_NAME,
                    PLACEHOLDER_CPK_PUBLIC_KEY_HASHES,
                    isPlatformClass = true,
                    bundle.symbolicName
                )
            }
        }

        val sandbox = sandboxes.find { sandbox -> sandbox.containsBundle(bundle) } ?: throw SandboxException(
            "Bundle ${bundle.symbolicName} was not found in the sandbox group or in the platform sandbox."
        )

        return if (isStaticTag) {
            StaticTag(sandbox.cpk.cpkHash, isPlatformClass = false, bundle.symbolicName)
        } else {
            EvolvableTag(
                sandbox.cordappBundle.symbolicName,
                sandbox.cpk.id.signers,
                isPlatformClass = false,
                bundle.symbolicName
            )
        }
    }
}