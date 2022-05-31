package net.corda.applications.workers.rpc.test.annotation

import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.platform.commons.util.AnnotationUtils.findAnnotation;
import java.net.HttpURLConnection
import java.net.URL
import java.util.Optional

/**
 * Endpoint availability condition
 *
 * @constructor Create empty Endpoint availability condition
 */
class EndpointAvailabilityCondition : ExecutionCondition {

    override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult {

        var optional: Optional<SkipWhenLocalClusterUnavailable> =
            findAnnotation(context.element, SkipWhenLocalClusterUnavailable::class.java)

        if (optional.isPresent) {
            val annotation = optional.get();

            var result = (!checkForJenkins() && checkEndpoint("http://localhost:8888"))
            //check connection here
            return if (result) {
                ConditionEvaluationResult.enabled("Connection is up")
            } else {
                ConditionEvaluationResult.disabled("Connection is down, skipping test")
            }
        }
        return ConditionEvaluationResult.enabled("No assumptions, moving on...")
    }

    /**
     * Check for jenkins by searching for the JENKINS_URL env variable
     *
     * @return true if present, false if absent
     */
    private fun checkForJenkins(): Boolean {
        return !System.getenv("JENKINS_URL").isNullOrBlank()
    }


    /**
     * Check endpoint - Checks to see if the supplied endpoint is reachable
     *
     * @param endpoint the URL to be checked
     * @return true if reachable, false if not
     */
    private fun checkEndpoint(endpoint: String): Boolean {
        val url = URL(endpoint)
        val connection = url.openConnection() as HttpURLConnection
        return connection.responseCode == 200
    }

}