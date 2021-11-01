package net.corda.sandboxhooks

import net.corda.v5.application.flows.Flow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
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
    fun `can create class tags for a non-bundle class and use it to retrieve the class`() {
        val nonBundleClasses = setOf(
            String::class.java, // A class from the Kotlin bundle.
            ClassLoader::class.java // A class from a Corda bundle.
        )
        nonBundleClasses.forEach { nonCpkClass ->
            val staticTag = sandboxLoader.group1.getStaticTag(nonCpkClass)
            val evolvableTag = sandboxLoader.group1.getStaticTag(nonCpkClass)

            assertEquals(nonCpkClass, sandboxLoader.group1.getClass(nonCpkClass.name, staticTag))
            assertEquals(nonCpkClass, sandboxLoader.group1.getClass(nonCpkClass.name, evolvableTag))
        }
    }

    @Test
    fun `can create class tags for a non-CPK class and use it to retrieve the class`() {
        val nonCpkClasses = setOf(
            Lazy::class.java, // A class from the Kotlin bundle.
            Flow::class.java // A class from a Corda bundle.
        )
        nonCpkClasses.forEach { nonCpkClass ->
            val staticTag = sandboxLoader.group1.getStaticTag(nonCpkClass)
            val evolvableTag = sandboxLoader.group1.getStaticTag(nonCpkClass)

            assertEquals(nonCpkClass, sandboxLoader.group1.getClass(nonCpkClass.name, staticTag))
            assertEquals(nonCpkClass, sandboxLoader.group1.getClass(nonCpkClass.name, evolvableTag))
        }
    }

    @Test
    fun `can create class tags for a CPK class and use it to retrieve the class`() {
        val cpkClass = sandboxLoader.group1.loadClassFromMainBundles(LIBRARY_QUERY_CLASS, Any::class.java)
        val staticTag = sandboxLoader.group1.getStaticTag(cpkClass)
        val evolvableTag = sandboxLoader.group1.getStaticTag(cpkClass)

        assertEquals(cpkClass, sandboxLoader.group1.getClass(cpkClass.name, staticTag))
        assertEquals(cpkClass, sandboxLoader.group1.getClass(cpkClass.name, evolvableTag))
    }
}