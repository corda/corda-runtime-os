package net.corda.sandbox.internal

import java.util.Collections.unmodifiableMap
import java.util.Collections.unmodifiableSortedMap
import java.util.SortedMap
import java.util.TreeMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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
import org.osgi.framework.Bundle
import org.slf4j.LoggerFactory

/**
 * An implementation of the [SandboxGroup] interface.
 *
 * @property id Unique identifier for this sandbox group.
 * @property cpkSandboxes The CPK sandboxes in this sandbox group.
 * @param publicSandboxes An iterable containing all existing public sandboxes.
 * @param classTagFactory Used to generate class tags.
 * @param bundleUtils The utils that all OSGi activity is delegated to for testing purposes.
 */
internal class SandboxGroupImpl(
    override val id: UUID,
    override val cpkSandboxes: Collection<CpkSandbox>,
    private val publicSandboxes: Iterable<Sandbox>,
    private val classTagFactory: ClassTagFactory,
    private val bundleUtils: BundleUtils
) : SandboxGroupInternal {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private val primitiveTypes = unmodifiableMap(setOf(
            Long::class.java,
            Int::class.java,
            Short::class.java,
            Byte::class.java,
            Char::class.java,
            Boolean::class.java,
            Double::class.java,
            Float::class.java
        ).associateBy(Class<*>::getName))

        private fun Throwable.withSuppressed(exceptions: Iterable<Throwable>): Throwable {
            exceptions.forEach(::addSuppressed)
            return this
        }
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
        val suppressed = mutableListOf<Exception>()
        return cpkSandboxes.mapNotNullTo(HashSet()) { sandbox ->
            try {
                sandbox.loadClassFromMainBundle(className)
            } catch (e: SandboxException) {
                suppressed += e
                null
            }
        }.singleOrNull()
            ?: throw SandboxException("Class $className was not found in any sandbox in the sandbox group.")
                .withSuppressed(suppressed)
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

    private val staticTagCache = ConcurrentHashMap<Class<*>, String>()
    private val evolvableTagCache = ConcurrentHashMap<Class<*>, String>()
    private val reverseTagCache = ConcurrentHashMap<Pair<String, String>, Class<*>>()

    override fun getStaticTag(klass: Class<*>) = staticTagCache.computeIfAbsent(klass) {
        getClassTag(klass, isStaticTag = true)
    }

    override fun getEvolvableTag(klass: Class<*>) = evolvableTagCache.computeIfAbsent(klass) {
        getClassTag(klass, isStaticTag = false)
    }

    @Suppress("ComplexMethod", "NestedBlockDepth", "MaxLineLength")
    override fun getClass(className: String, serialisedClassTag: String): Class<*> {
        return reverseTagCache.computeIfAbsent((className to serialisedClassTag)){
            val classTag = classTagFactory.deserialise(serialisedClassTag)

            when (classTag.classType) {
                ClassType.NonBundleClass -> {
                    try {
                        primitiveTypes[className] ?: bundleUtils.loadClassFromSystemBundle(className)
                    } catch (e: ClassNotFoundException) {
                        throw SandboxException(
                            "Class $className was not from a bundle, and could not be found in the system classloader."
                        )
                    }
                }

                ClassType.CpkSandboxClass -> {
                    val (sandbox, bundleName) = when (classTag) {
                        is StaticTag -> cpkSandboxes.find { sandbox -> sandbox.cpkMetadata.fileChecksum == classTag.cpkFileHash }
                            ?.let { Pair(it, classTag.classBundleName) }

                        is EvolvableTag -> {
                            cpkSandboxes.find {
                                it.cpkMetadata.cpkId.signerSummaryHash == classTag.cpkSignerSummaryHash &&
                                        (it.cpkMetadata.cpkId.name == classTag.cordaCpkCordappName || // CPK given names match or
                                                it.mainBundle.symbolicName == classTag.classBundleName) // symbolic names of class bundle match
                            }?.let { Pair(it, it.mainBundle.symbolicName) } // only load evolvable classes from the main bundle
                        }
                    } ?: throw SandboxException(
                        "Class tag $serialisedClassTag did not match any sandbox in the sandbox group."
                    )

                    sandbox.loadClass(className, bundleName) ?: throw SandboxException(
                        "Class $className could not be loaded from bundle $bundleName in sandbox ${sandbox.id}."
                    )
                }

                ClassType.PublicSandboxClass -> {
                    publicSandboxes.asSequence().mapNotNull { publicSandbox ->
                        publicSandbox.loadClass(className, classTag.classBundleName)
                    }.firstOrNull() ?: throw SandboxException(
                        "Class $className from bundle ${classTag.classBundleName} could not be loaded from any of the public sandboxes."
                    )
                }
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