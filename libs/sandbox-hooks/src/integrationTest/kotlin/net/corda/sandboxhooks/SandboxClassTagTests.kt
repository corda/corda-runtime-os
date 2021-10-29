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

    // TODO: Known to fail. Need to fix.
//    @Test
//    fun `can create tags for a platform classes and use them to retrieve the class`() {
//        val platformClasses = setOf(String::class.java, List::class.java, ClassLoader::class.java)
//        platformClasses.forEach { platformClass ->
//            val staticTag = sandboxLoader.group1.getStaticTag(platformClass)
//            val evolvableTag = sandboxLoader.group1.getStaticTag(platformClass)
//
//            assertEquals(platformClass, sandboxLoader.group1.getClass(platformClass.name, staticTag))
//            assertEquals(platformClass, sandboxLoader.group1.getClass(platformClass.name, evolvableTag))
//        }
//    }

    @Test
    fun `can create a static tag for a public sandbox class and use it to retrieve the class`() {
        val publicSandboxClasses = setOf(
            Flow::class.java, // A class from a Corda bundle.
            Lazy::class.java // A class from the Kotlin bundle.
        )
        publicSandboxClasses.forEach { publicSandboxClass ->
            val staticTag = sandboxLoader.group1.getStaticTag(publicSandboxClass)
            val evolvableTag = sandboxLoader.group1.getStaticTag(publicSandboxClass)

            assertEquals(publicSandboxClass, sandboxLoader.group1.getClass(publicSandboxClass.name, staticTag))
            assertEquals(publicSandboxClass, sandboxLoader.group1.getClass(publicSandboxClass.name, evolvableTag))
        }
    }

    @Test
    fun `can create a static tag for a sandbox class and use it to retrieve the class`() {
        val sandboxClass = sandboxLoader.sandbox1.loadClassFromCordappBundle(LIBRARY_QUERY_CLASS)
        val staticTag = sandboxLoader.group1.getStaticTag(sandboxClass)
        val evolvableTag = sandboxLoader.group1.getStaticTag(sandboxClass)

        assertEquals(sandboxClass, sandboxLoader.group1.getClass(sandboxClass.name, staticTag))
        assertEquals(sandboxClass, sandboxLoader.group1.getClass(sandboxClass.name, evolvableTag))
    }
}