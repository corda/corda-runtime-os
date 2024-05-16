package net.corda.gradle.plugin.smoke

import net.corda.gradle.plugin.FunctionalBaseTest
import net.corda.gradle.plugin.queries.LIST_CPIS_TASK_NAME
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext

class CustomCondition : ExecutionCondition {
    override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult {
        return if (isCombinedWorker()) {
            ConditionEvaluationResult.enabled("Test is enabled.")
        } else {
            ConditionEvaluationResult.disabled("Test is disabled.")
        }
    }

    companion object {
        fun isCombinedWorker(): Boolean {
            // Your custom condition here
            // TODO: Determine whether combined worker is available
            return false
        }
    }
}

@ExtendWith(CustomCondition::class)
class FakeTest : FunctionalBaseTest() {

    @Test
    fun fakeTest() {
        // This is a fake test to make sure the smoke tests are run
        println("This is fake test to make sure the smoke tests are run")
        throw RuntimeException("This is a fake test")
    }

    @Test
    fun listCPIsTest() {
        appendCordaRuntimeGradlePluginExtension()
        val result = executeWithRunner(LIST_CPIS_TASK_NAME)
        assertTrue(result.output.contains(Regex("CpiName.*CpiVersion.*CpiFileCheckSum")))
    }
}
