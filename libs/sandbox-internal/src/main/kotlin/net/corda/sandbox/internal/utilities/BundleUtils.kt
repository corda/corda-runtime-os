package net.corda.sandbox.internal.utilities

import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.Constants.SYSTEM_BUNDLE_ID
import org.osgi.framework.FrameworkListener
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.wiring.BundleRevision.PACKAGE_NAMESPACE
import org.osgi.framework.wiring.BundleWiring
import org.osgi.framework.wiring.FrameworkWiring
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.runtime.ServiceComponentRuntime
import java.util.Collections.unmodifiableMap
import java.util.Collections.unmodifiableSet

/** Handles bundle operations for the `SandboxCreationService` and the `SandboxContextService`. */
@Component(service = [BundleUtils::class])
internal class BundleUtils @Activate constructor(
    @Reference
    private val serviceComponentRuntime: ServiceComponentRuntime,
    private val bundleContext: BundleContext
) {
    private val systemBundle = bundleContext.getBundle(SYSTEM_BUNDLE_ID)
    private val arrayType = "\\[++(([BCDFIJSZ])|L([^;]++);)".toRegex()

    /** Determine which packages the system bundle exports by examining its capabilities. */
    private val systemPackageNames = unmodifiableSet(
        systemBundle.adapt(BundleWiring::class.java)
            .getCapabilities(PACKAGE_NAMESPACE)
            .mapNotNull { capability ->
                capability.attributes[PACKAGE_NAMESPACE].toString()
            }.filterNotTo(linkedSetOf()) { packageName ->
                packageName.startsWith("java.")
            }
    )

    private val primitiveTypes = unmodifiableMap(
        setOf(
            Long::class.java,
            Int::class.java,
            Short::class.java,
            Byte::class.java,
            Char::class.java,
            Boolean::class.java,
            Double::class.java,
            Float::class.java
        ).associateBy(Class<*>::getName)
    )

    private val primitiveTypeNames = unmodifiableSet(setOf("B", "C", "D", "F", "I", "J", "S", "Z"))

    private fun typeNameOf(className: String): String {
        return arrayType.matchEntire(className)?.let { it.groupValues[2] + it.groupValues[3] } ?: className
    }

    private fun isSystemType(typeName: String): Boolean {
        return typeName in primitiveTypeNames || typeName.substringBeforeLast('.').let { packageName ->
            packageName.startsWith("java.") || packageName in systemPackageNames
        }
    }

    /** Returns the bundle from which [klass] is loaded, or null if there is no such bundle. */
    fun getBundle(klass: Class<*>): Bundle? = FrameworkUtil.getBundle(klass) ?: try {
        // The lookup approach above does not work for the system bundle.
        if (!klass.isPrimitive && loadClassFromSystemBundle(klass.name) === klass) {
            systemBundle
        } else {
            null
        }
    } catch (e: ClassNotFoundException) {
        null
    }

    /**
     * Loads OSGi framework types. Can also be used to load Java platform types.
     * Restrict which classes it can try to load by comparing this class's package
     * name with those package names exported by the system bundle's wirings.
     */
    @Throws(ClassNotFoundException::class)
    fun loadClassFromSystemBundle(className: String): Class<*> {
        return primitiveTypes[className] ?: if (isSystemType(typeNameOf(className))) {
            systemBundle.loadClass(className)
        } else {
            throw ClassNotFoundException(className)
        }
    }

    /**
     * Returns the bundle from which [serviceComponentRuntime] is loaded, or null if there is no such bundle.
     *
     * This exists to simplify mocking - we can provide one mock for recovering the `ServiceComponentRuntime` bundle
     * during `SandboxServiceImpl` initialisation, and another mock for general retrieval of bundles based on classes.
     */
    fun getServiceRuntimeComponentBundle(): Bundle? = FrameworkUtil.getBundle(serviceComponentRuntime::class.java)

    /** Returns the list of all installed bundles. */
    val allBundles get() = bundleContext.bundles.toList()

    /**
     * Force the update or removal of bundles.
     */
    fun refreshBundles(bundles: Collection<Bundle>, refreshListener: FrameworkListener) {
        systemBundle.adapt(FrameworkWiring::class.java).refreshBundles(bundles, refreshListener)
    }

    /**
     * Resolve all unresolved members of [bundles].
     */
    fun resolveBundles(bundles: Collection<Bundle>): Boolean {
        return systemBundle.adapt(FrameworkWiring::class.java).resolveBundles(bundles)
    }
}
