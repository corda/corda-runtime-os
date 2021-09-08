package net.corda.sandbox.internal

import net.corda.packaging.Cpk
import net.corda.sandbox.AMQPClassTag
import net.corda.sandbox.ClassTag
import net.corda.sandbox.KryoClassTag
import net.corda.sandbox.SandboxException
import net.corda.sandbox.SandboxGroup
import net.corda.sandbox.internal.sandbox.CpkSandboxImpl
import net.corda.sandbox.internal.sandbox.SandboxInternal
import net.corda.sandbox.internal.utilities.BundleUtils
import net.corda.v5.crypto.SecureHash
import java.util.Collections
import java.util.NavigableMap

/**
 * An implementation of the [SandboxGroup] interface.
 *
 * @param bundleUtils The [BundleUtils] that all OSGi activity is delegated to for testing purposes
 * @param sandboxesById The [CpkSandboxImpl]s in this sandbox group, keyed by the identifier of their CPK
 * @param platformSandbox The sandbox containing all the platform (i.e. non CPK) bundles
 */
internal class SandboxGroupImpl(
    private val bundleUtils: BundleUtils,
    private val sandboxesById: NavigableMap<Cpk.Identifier, CpkSandboxImpl>,
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

    override fun getKryoClassTag(klass: Class<*>): KryoClassTag {
        val bundle = bundleUtils.getBundle(klass)
            ?: throw SandboxException("Class $klass is not loaded from any bundle.")

        return if (platformSandbox.containsBundle(bundle)) {
            KryoClassTag(PLACEHOLDER_CPK_FILE_HASH, isPlatformClass = true, bundle.symbolicName)
        } else {
            val sandbox = sandboxes.find { sandbox -> sandbox.containsBundle(bundle) }
                ?: throw SandboxException("Bundle $bundle is not contained in any sandbox.")
            KryoClassTag(sandbox.cpk.cpkHash, isPlatformClass = false, bundle.symbolicName)
        }
    }

    override fun getAMQPClassTag(klass: Class<*>): AMQPClassTag {
        val bundle = bundleUtils.getBundle(klass)
            ?: throw SandboxException("Class $klass is not loaded from any bundle.")

        return if (platformSandbox.containsBundle(bundle)) {
            AMQPClassTag(
                PLACEHOLDER_CORDAPP_BUNDLE_NAME,
                PLACEHOLDER_CPK_PUBLIC_KEY_HASHES,
                isPlatformClass = true,
                bundle.symbolicName
            )
        } else {
            val sandbox = sandboxes.find { sandbox -> sandbox.containsBundle(bundle) }
                ?: throw SandboxException("Bundle $bundle is not contained in any sandbox.")
            AMQPClassTag(
                sandbox.cordappBundle.symbolicName,
                sandbox.cpk.id.signers,
                isPlatformClass = false,
                bundle.symbolicName
            )
        }
    }

    override fun getClass(className: String, classTag: ClassTag): Class<*>? {
        return if (classTag.isPlatformClass) {
            platformSandbox.loadClass(className, classTag.classBundleName)
        } else {
            val sandbox = when (classTag) {
                is KryoClassTag -> getSandboxFromKryoClassTag(classTag)
                is AMQPClassTag -> getSandboxFromAMQPClassTag(classTag)
            }
            sandbox.loadClass(className, classTag.classBundleName)
        }
    }


    /** Returns the [CpkSandboxImpl] identified by the [kryoClassTag]. */
    private fun getSandboxFromKryoClassTag(kryoClassTag: KryoClassTag) = sandboxes.find { sandbox ->
        sandbox.cpk.cpkHash == kryoClassTag.cpkFileHash
    } ?: throw SandboxException(
        "The sandbox group does not contain a sandbox for the CPK with the requested hash, ${kryoClassTag.cpkFileHash}."
    )

    /** Returns the [CpkSandboxImpl] identified by the [amqpClassTag]. */
    private fun getSandboxFromAMQPClassTag(amqpClassTag: AMQPClassTag) = sandboxes.find { sandbox ->
        sandbox.cpk.id.signers == amqpClassTag.cpkPublicKeyHashes
                && sandbox.cordappBundle.symbolicName == amqpClassTag.cordappBundleName
    } ?: throw SandboxException(
        "The sandbox group does not contain a sandbox for the CPK with the requested signers, " +
                "${amqpClassTag.cpkPublicKeyHashes}, and the requested CorDapp bundle symbolic name, " +
                "${amqpClassTag.cordappBundleName}."
    )
}