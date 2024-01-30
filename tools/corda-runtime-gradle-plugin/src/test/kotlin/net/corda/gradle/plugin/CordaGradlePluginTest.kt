package net.corda.gradle.plugin

//import net.corda.craft5.annotations.TestSuite
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

//@TestSuite
class CordaGradlePluginTest : FunctionalBaseTest() {

    @Test
    fun buildWithExtension() {
        appendCordaRuntimeGradlePluginExtension()
        assertDoesNotThrow { executeWithRunner() }
    }
}
