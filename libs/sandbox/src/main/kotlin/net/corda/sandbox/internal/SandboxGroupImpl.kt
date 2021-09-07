package net.corda.sandbox.internal

import net.corda.packaging.Cpk
import net.corda.sandbox.ClassTag
import net.corda.sandbox.AMQPClassTag
import net.corda.sandbox.SandboxException
import net.corda.sandbox.SandboxGroup
import net.corda.sandbox.KryoClassTag
import net.corda.sandbox.internal.sandbox.CpkSandboxImpl
import net.corda.sandbox.internal.utilities.BundleUtils
import java.util.NavigableMap

/**
 * An implementation of the [SandboxGroup] interface.
 *
 * @param bundleUtils The [BundleUtils] that all OSGi activity is delegated to for testing purposes
 * @param sandboxesById The [CpkSandboxImpl]s in this sandbox group, keyed by the identifier of their CPK
 */
internal class SandboxGroupImpl(
    private val bundleUtils: BundleUtils,
    private val sandboxesById: NavigableMap<Cpk.Identifier, CpkSandboxImpl>
) : SandboxGroup {

    override val sandboxes = sandboxesById.values

    override fun getSandbox(cpkIdentifier: Cpk.Identifier) = sandboxesById[cpkIdentifier]
        ?: throw SandboxException("No sandbox was found in the group that had the CPK identifier $cpkIdentifier.")

    override fun loadClassFromCordappBundle(cpkIdentifier: Cpk.Identifier, className: String) =
        getSandbox(cpkIdentifier).loadClassFromCordappBundle(className)

    override fun <T : Any> loadClassFromCordappBundle(className: String, type: Class<T>): Class<out T> {
        val containingSandbox = sandboxes.find { sandbox -> sandbox.cordappBundleContainsClass(className) }
            ?: throw SandboxException("Class $className could not be not found in sandbox group.")

        return try {
            containingSandbox.loadClassFromCordappBundle(className).asSubclass(type)
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
        val sandbox = sandboxes.find { sandbox -> sandbox.containsClass(klass) }
            ?: throw SandboxException("Class $klass is not contained in any sandbox.")

        val bundle = bundleUtils.getBundle(klass)
            ?: throw SandboxException("Class $klass is not loaded from any bundle.")

        return KryoClassTag(sandbox.cpk.cpkHash, bundle.symbolicName)
    }

    override fun getAMQPClassTag(klass: Class<*>): AMQPClassTag {
        val sandbox = sandboxes.find { sandbox -> sandbox.containsClass(klass) }
            ?: throw SandboxException("Class $klass is not contained in any sandbox.")

        val bundle = bundleUtils.getBundle(klass)
            ?: throw SandboxException("Class $klass is not loaded from any bundle.")

        return AMQPClassTag(
            sandbox.cordappBundle.symbolicName,
            sandbox.cpk.id.signers,
            bundle.symbolicName
        )
    }

    override fun getClass(className: String, classTag: ClassTag) = when (classTag) {
        is KryoClassTag -> getClassFromKryoClassTag(className, classTag)
        is AMQPClassTag -> getClassFromAMQPClassTag(className, classTag)
    }

    /** Returns the [Class] identified by the [className] and the [kryoClassTag]. */
    private fun getClassFromKryoClassTag(className: String, kryoClassTag: KryoClassTag): Class<*> {
        val sandbox = sandboxes.find { sandbox -> sandbox.cpk.cpkHash == kryoClassTag.cpkFileHash }
            ?: throw SandboxException("Bad bad bad") // TODO - Update exception.

        val bundle = sandbox.getBundle(kryoClassTag.classBundleName)
            ?: throw SandboxException("Bad bad bad") // TODO - Update exception.

        // TODO - Handle exception.
        return bundleUtils.loadClass(bundle, className)
    }

    /** Returns the [Class] identified by the [className] and the [amqpClassTag]. */
    private fun getClassFromAMQPClassTag(className: String, amqpClassTag: AMQPClassTag): Class<*> {
        val sandbox = sandboxes.find { sandbox ->
            sandbox.cpk.id.signers == amqpClassTag.cpkPublicKeyHashes
                    && sandbox.cordappBundle.symbolicName == amqpClassTag.cordappBundleName
        } ?: throw SandboxException("Bad bad bad") // TODO - Update exception.

        val bundle = sandbox.getBundle(amqpClassTag.classBundleName)
            ?: throw SandboxException("Bad bad bad") // TODO - Update exception.

        // TODO - Handle exception.
        return bundleUtils.loadClass(bundle, className)
    }
}