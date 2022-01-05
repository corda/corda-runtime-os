package net.corda.cli.plugins.mgm

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GenerateGroupPolicyTest {

    private class DummyGroupPolicyOutput: GroupPolicyOutput {
        private val outputBuilder = StringBuilder()

        fun getOutput(): String {
            return outputBuilder.toString()
        }

        override fun generateOutput(content: String) {
            // needs clearing in case it will be used multiple times
            outputBuilder.clear()
            outputBuilder.append(content)
        }
    }

    @Test
    fun `command generates and prints GroupPolicy to output as correctly formatted JSON`() {
        val dummyOutput = DummyGroupPolicyOutput()
        val generateGroupPolicyCommand = GenerateGroupPolicy(dummyOutput)

        generateGroupPolicyCommand.run()

        assertNotEquals(0, dummyOutput.getOutput().length)
        assertTrue(isValidJson(dummyOutput.getOutput()))

        val om = jacksonObjectMapper()
        val parsed = om.readValue<Map<String, Any>>(dummyOutput.getOutput())

        assertEquals(1, parsed["fileFormatVersion"])
    }

    /**
     * Checks that the [content] String is a valid JSON.
     */
    private fun isValidJson(content: String): Boolean {
        return try {
            jacksonObjectMapper().readTree(content)
            true
        } catch (e: Exception) {
            false
        }
    }
}