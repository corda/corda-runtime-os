package net.corda.sandboxtests

import net.corda.sandbox.SandboxException
import net.corda.v5.application.flows.Flow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.assertThrows
import org.osgi.framework.Constants.SYSTEM_BUNDLE_ID
import org.osgi.framework.FrameworkUtil
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

/** Tests the use of class tags for serialisation and deserialisation. */
@ExtendWith(ServiceExtension::class)
class SandboxClassTagTests {
    companion object {
        @InjectService(timeout = 1000)
        lateinit var sandboxLoader: SandboxLoader
    }

    @Test
    fun `can create class tags for a non-bundle class and use them to retrieve the class`() {
        val nonBundleClasses = setOf(
            String::class.java, // A system class.
            ClassLoader::class.java // Another system class.
        )
        nonBundleClasses.forEach { nonCpkClass ->
            val staticTag = sandboxLoader.group1.getStaticTag(nonCpkClass)
            val evolvableTag = sandboxLoader.group1.getEvolvableTag(nonCpkClass)

            assertEquals(nonCpkClass, sandboxLoader.group1.getClass(nonCpkClass.name, staticTag))
            assertEquals(nonCpkClass, sandboxLoader.group1.getClass(nonCpkClass.name, evolvableTag))
        }
    }

    @Test
    fun `can create class tags for a non-CPK class and use them to retrieve the class`() {
        val nonCpkClasses = setOf(
            Lazy::class.java, // A class from the Kotlin bundle.
            Flow::class.java // A class from a Corda bundle.
        )
        nonCpkClasses.forEach { nonCpkClass ->
            val staticTag = sandboxLoader.group1.getStaticTag(nonCpkClass)
            val evolvableTag = sandboxLoader.group1.getEvolvableTag(nonCpkClass)

            assertEquals(nonCpkClass, sandboxLoader.group1.getClass(nonCpkClass.name, staticTag))
            assertEquals(nonCpkClass, sandboxLoader.group1.getClass(nonCpkClass.name, evolvableTag))
        }
    }

    @Test
    fun `can create class tags for a CPK main bundle class and use them to retrieve the class`() {
        val cpkClass = sandboxLoader.group1.loadClassFromMainBundles(SERVICES_FLOW_CPK_1)
        val staticTag = sandboxLoader.group1.getStaticTag(cpkClass)
        val evolvableTag = sandboxLoader.group1.getEvolvableTag(cpkClass)

        assertEquals(cpkClass, sandboxLoader.group1.getClass(cpkClass.name, staticTag))
        assertEquals(cpkClass, sandboxLoader.group1.getClass(cpkClass.name, evolvableTag))
    }

    @Test
    fun `can create static tag for a CPK library class and use it to retrieve the class`() {
        val cpkFlowClass = sandboxLoader.group1.loadClassFromMainBundles(SERVICES_FLOW_CPK_1)
        val cpkLibClass = FrameworkUtil.getBundle(cpkFlowClass).loadClass(LIBRARY_QUERY_CLASS)
        val staticTag = sandboxLoader.group1.getStaticTag(cpkLibClass)

        assertEquals(cpkLibClass, sandboxLoader.group1.getClass(cpkLibClass.name, staticTag))
    }

    @Test
    fun `throws if attempted to create evolvable tag for a CPK library class`() {
        val cpkFlowClass = sandboxLoader.group1.loadClassFromMainBundles(SERVICES_FLOW_CPK_1)
        val cpkLibClass = FrameworkUtil.getBundle(cpkFlowClass).loadClass(LIBRARY_QUERY_CLASS)

        assertThrows<SandboxException> {
            sandboxLoader.group1.getEvolvableTag(cpkLibClass)
        }
    }

    @Test
    fun `can create class tags for system bundle classes and use them to retrieve the class`() {
        val systemBundle = FrameworkUtil.getBundle(this::class.java).bundleContext.getBundle(SYSTEM_BUNDLE_ID)
        val systemBundleClass = systemBundle.loadClass(SYSTEM_BUNDLE_CLASS)

        val staticTag = sandboxLoader.group1.getStaticTag(systemBundleClass)
        val evolvableTag = sandboxLoader.group1.getEvolvableTag(systemBundleClass)

        assertEquals(systemBundleClass, sandboxLoader.group1.getClass(systemBundleClass.name, staticTag))
        assertEquals(systemBundleClass, sandboxLoader.group1.getClass(systemBundleClass.name, evolvableTag))
    }
}