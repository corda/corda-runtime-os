package net.corda.sandbox.internal

import net.corda.packaging.Cpk
import net.corda.sandbox.AMQPClassTag
import net.corda.sandbox.ClassTag
import net.corda.sandbox.KryoClassTag
import net.corda.sandbox.SandboxException
import net.corda.sandbox.SandboxGroup
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

    override fun loadClassFromCordappBundle(cpkIdentifier: Cpk.Identifier, className: String) =
        getSandbox(cpkIdentifier)?.loadClassFromCordappBundle(className)

    override fun <T : Any> loadClassFromCordappBundle(className: String, type: Class<T>): Class<out T>? {
        val containingSandbox = sandboxes.find { sandbox -> sandbox.cordappBundleContainsClass(className) }
        val klass = containingSandbox?.loadClassFromCordappBundle(className)

        return try {
            klass?.asSubclass(type)
        } catch (e: ClassCastException) {
            throw SandboxException(
                "Class $className was found in sandbox, but was not of the provided type $type."
            )
        }
    }

    override fun cordappClassCount(className: String) = sandboxes.count { sandbox ->
        sandbox.cordappBundleContainsClass(className)
    }

    override fun getKryoClassTag(klass: Class<*>) = getClassTag(klass, isKryoClassTag = true) as KryoClassTag?

    override fun getAMQPClassTag(klass: Class<*>) = getClassTag(klass, isKryoClassTag = false) as AMQPClassTag?

    override fun getClass(className: String, classTag: ClassTag): Class<*>? {
        val sandbox = if (classTag.isPlatformClass) {
            platformSandbox

        } else {
            when (classTag) {
                is KryoClassTag -> sandboxes.find { sandbox -> sandbox.cpk.cpkHash == classTag.cpkFileHash }
                is AMQPClassTag -> sandboxes.find { sandbox ->
                    sandbox.cpk.id.signers == classTag.cpkPublicKeyHashes
                            && sandbox.cordappBundle.symbolicName == classTag.cordappBundleName
                }
                else -> throw SandboxException("Unrecognised class tag type ${classTag::class.java.name}.")
            }
        }

        return sandbox?.loadClass(className, classTag.classBundleName)
    }

    /**
     * Returns the [ClassTag] for a given [klass]. Returns null if the class is not contained in any bundle, or is
     * contained in a bundle that is not contained in any sandbox in the group.
     *
     * If [isKryoClassTag] is true, a [KryoClassTag] is returned. Otherwise, an [AMQPClassTag] is returned.
     */
    private fun getClassTag(klass: Class<*>, isKryoClassTag: Boolean): ClassTag? {
        val bundle = bundleUtils.getBundle(klass) ?: return null

        if (platformSandbox.containsBundle(bundle)) {
            return if (isKryoClassTag) {
                KryoClassTag(PLACEHOLDER_CPK_FILE_HASH, isPlatformClass = true, bundle.symbolicName)
            } else {
                AMQPClassTag(
                    PLACEHOLDER_CORDAPP_BUNDLE_NAME,
                    PLACEHOLDER_CPK_PUBLIC_KEY_HASHES,
                    isPlatformClass = true,
                    bundle.symbolicName
                )
            }
        }

        val sandbox = sandboxes.find { sandbox -> sandbox.containsBundle(bundle) } ?: return null
        return if (isKryoClassTag) {
            KryoClassTag(sandbox.cpk.cpkHash, isPlatformClass = false, bundle.symbolicName)
        } else {
            AMQPClassTag(
                sandbox.cordappBundle.symbolicName,
                sandbox.cpk.id.signers,
                isPlatformClass = false,
                bundle.symbolicName
            )
        }
    }
}