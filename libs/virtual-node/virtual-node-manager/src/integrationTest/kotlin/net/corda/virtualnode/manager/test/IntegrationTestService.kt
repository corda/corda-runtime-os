package net.corda.virtualnode.manager.test

import net.corda.packaging.CPI
import net.corda.packaging.CPK
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxGroup
import net.corda.v5.application.flows.Flow
import net.corda.virtualnode.manager.api.RuntimeRegistration
import net.corda.virtualnode.manager.test.Constants.PLATFORM_PUBLIC_BUNDLE_NAMES
import org.junit.jupiter.api.fail
import org.osgi.framework.FrameworkUtil
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

class IntegrationTestService constructor(
    private val sandboxCreationService: SandboxCreationService,
    private val registration: RuntimeRegistration,
    private val baseDir: Path
) {
    init {
        val allBundles = FrameworkUtil.getBundle(this::class.java).bundleContext.bundles
        val (publicBundles, privateBundles) = allBundles.partition { bundle ->
            bundle.symbolicName in PLATFORM_PUBLIC_BUNDLE_NAMES
        }
        sandboxCreationService.createPublicSandbox(publicBundles, privateBundles)
    }

    private fun loadResource(resourceName: String): URI {
        return (this::class.java.classLoader.getResource(resourceName)
            ?: fail("Failed to load $resourceName")).toURI()
    }

    private fun getInputStream(resourceName: String): InputStream =
        loadResource(resourceName).toURL().openStream().buffered()

    fun loadCPIFromResource(resourceName: String): CPI = getInputStream(resourceName).use {
        CPI.from(
            it,
            expansionLocation = Files.createTempDirectory(baseDir, "cpb"),
            verifySignature = true
        )
    }

    fun createSandboxGroupFor(cpks: Collection<CPK>): SandboxGroup =
        sandboxCreationService.createSandboxGroup(cpks.toSet())

    fun registerCrypto(sandboxGroup: SandboxGroup) = registration.register(sandboxGroup)

    fun unloadSandboxGroup(sandboxGroup: SandboxGroup) {
        registration.unregister(sandboxGroup)
        // Unload from OSGi and the process.
        sandboxCreationService.unloadSandboxGroup(sandboxGroup)
    }

    private fun <T, U : T> getServiceFor(serviceType: Class<T>, bundleClass: Class<U>): T {
        val context = FrameworkUtil.getBundle(bundleClass).bundleContext
        return context.getServiceReferences(serviceType, null)
            .map(context::getService)
            .filterIsInstance(bundleClass)
            .firstOrNull() ?: fail("No service for $serviceType.")
    }

    internal fun <T : Any> runFlow(className: String, group: SandboxGroup): T {
        val workflowClass = group.loadClassFromMainBundles(className, Flow::class.java)
        @Suppress("unchecked_cast")
        return getServiceFor(Flow::class.java, workflowClass).call() as? T
            ?: fail("Workflow did not return the correct type.")
    }
}
