package net.corda.gradle.plugin

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class CordaGradlePluginTest : FunctionalBaseTest() {

    @Test
    fun buildWithExtension() {
        appendCordaRuntimeGradlePluginExtension()
        assertDoesNotThrow { executeWithRunner() }
    }
}
