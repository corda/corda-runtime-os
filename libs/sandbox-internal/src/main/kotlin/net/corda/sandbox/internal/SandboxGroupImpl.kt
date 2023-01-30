package net.corda.sandbox.internal

import net.corda.libs.packaging.core.CpkMetadata
import net.corda.sandbox.SandboxException
import net.corda.sandbox.SandboxGroup
import net.corda.sandbox.internal.classtag.ClassTagFactory
import net.corda.sandbox.internal.classtag.ClassType
import net.corda.sandbox.internal.classtag.EvolvableTag
import net.corda.sandbox.internal.classtag.StaticTag
import net.corda.sandbox.internal.sandbox.CpkSandbox
import net.corda.sandbox.internal.sandbox.Sandbox
import net.corda.sandbox.internal.utilities.BundleUtils
import net.corda.v5.base.util.debug
import org.osgi.framework.Bundle
import org.slf4j.LoggerFactory
import java.util.Collections.unmodifiableSortedMap
import java.util.SortedMap
import java.util.TreeMap

/**
 * An implementation of the [SandboxGroup] interface.
 *
 * @param cpkSandboxes The CPK sandboxes in this sandbox group.
 * @param publicSandboxes An iterable containing all existing public sandboxes.
 * @param classTagFactory Used to generate class tags.
 * @param bundleUtils The utils that all OSGi activity is delegated to for testing purposes.
 */
internal class SandboxGroupImpl(
    override val cpkSandboxes: Collection<CpkSandbox>,
    private val publicSandboxes: Iterable<Sandbox>,
    private val classTagFactory: ClassTagFactory,
    private val bundleUtils: BundleUtils
) : SandboxGroupInternal {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val metadata: SortedMap<Bundle, CpkMetadata> = unmodifiableSortedMap(cpkSandboxes.associateTo(TreeMap()) { cpk ->
        cpk.mainBundle to cpk.cpkMetadata
    })

    override fun loadClassFromPublicBundles(className: String): Class<*>? {
        val clazz = publicSandboxes
            .flatMap(Sandbox::publicBundles)
            .filterNot(Bundle::isFragment)
            .mapNotNullTo(LinkedHashSet()) { bundle ->
                try {
                    bundle.loadClass(className)
                } catch (e: ClassNotFoundException) {
                    logger.debug("Could not load class {} from bundle {}: {}", className, bundle, e.message)
                    null
                }
            }.singleOrNull()
        if (clazz == null) {
            logger.warn("Class {} was not found in any sandbox in the sandbox group.", className)
        }
        return clazz
    }

    override fun loadClassFromMainBundles(className: String): Class<*> {
        return cpkSandboxes.mapNotNullTo(HashSet()) { sandbox ->
            try {
                sandbox.loadClassFromMainBundle(className)
            } catch (e: SandboxException) {
                logger.debug {
                    "Could not load class $className from sandbox ${sandbox.cpkMetadata.mainBundle}: ${e.message}"
                }
                null
            }
        }.singleOrNull()
            ?: throw SandboxException("Class $className was not found in any sandbox in the sandbox group.")
    }

    override fun <T : Any> loadClassFromMainBundles(className: String, type: Class<T>): Class<out T> {
        val klass = loadClassFromMainBundles(className)
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

    @Suppress("ComplexMethod", "NestedBlockDepth")
    override fun getClass(className: String, serialisedClassTag: String): Class<*> {
        val classTag = classTagFactory.deserialise(serialisedClassTag)

        return when (classTag.classType) {
            ClassType.NonBundleClass -> {
                try {
                    bundleUtils.loadClassFromSystemBundle(className)
                } catch (e: ClassNotFoundException) {
                    throw SandboxException(
                        "Class $className was not from a bundle, and could not be found in the system classloader."
                    )
                }
            }

            ClassType.CpkSandboxClass -> {
                val sandbox = when (classTag) {
                    is StaticTag -> cpkSandboxes.find { sandbox -> sandbox.cpkMetadata.fileChecksum == classTag.cpkFileHash }
                    is EvolvableTag -> {
                        val sandbox = cpkSandboxes.find {
                            it.cpkMetadata.cpkId.signerSummaryHash == classTag.cpkSignerSummaryHash
                                    && it.mainBundle.symbolicName == classTag.mainBundleName
                        }
                        sandbox?.let {
                            if (classTag.classBundleName != it.mainBundle.symbolicName) {
                                throw SandboxException(
                                    "Attempted to load class $className with an evolvable class tag from cpk private bundle " +
                                            "${classTag.classBundleName}."
                                )
                            } else {
                                it
                            }
                        }
                    }
                } ?: throw SandboxException(
                    "Class tag $serialisedClassTag did not match any sandbox in the sandbox group."
                )
                sandbox.loadClass(className, classTag.classBundleName) ?: throw SandboxException(
                    "Class $className could not be loaded from bundle ${classTag.classBundleName} in sandbox ${sandbox.id}."
                )
            }

            ClassType.PublicSandboxClass -> {
                publicSandboxes.asSequence().mapNotNull { publicSandbox ->
                    publicSandbox.loadClass(className, classTag.classBundleName)
                }.firstOrNull() ?: throw SandboxException(
                    "Class $className from bundle ${classTag.classBundleName} could not be loaded from any of the public " +
                            "sandboxes."
                )
            }
        }
    }

    /**
     * Returns the serialised `ClassTag` for a given [klass].
     *
     * If [isStaticTag] is true, a serialised [StaticTag] is returned. Otherwise, a serialised [EvolvableTag] is
     * returned. For classed defined in CPKs, [EvolvableTag]s are only available if [klass] is defined in CPK's
     * main bundle.
     *
     * @throws [SandboxException] if the class is not contained in any bundle, if the class is contained in a bundle
     * that does not have a symbolic name, or if an [EvolvableTag] is requested for class defined in a CPK's private
     * bundle.
     */
    private fun getClassTag(klass: Class<*>, isStaticTag: Boolean): String {
        val bundle = bundleUtils.getBundle(klass)
            ?: return classTagFactory.createSerialisedTag(isStaticTag, null, null)

        val publicSandbox =
            publicSandboxes.find { sandbox -> sandbox.containsBundle(bundle) }
        if (publicSandbox != null) {
            return classTagFactory.createSerialisedTag(isStaticTag, bundle, null)
        }

        val cpkSandbox =
            cpkSandboxes.find { sandbox -> sandbox.containsBundle(bundle) }
                ?: throw SandboxException("Bundle ${bundle.symbolicName} was not found in the sandbox group or in a public sandbox.")
        if (bundle in cpkSandbox.privateBundles && !isStaticTag) {
            throw SandboxException("Attempted to create evolvable class tag for cpk private bundle ${bundle.symbolicName}.")
        }
        return classTagFactory.createSerialisedTag(isStaticTag, bundle, cpkSandbox)
    }
}