package net.corda.applications.workers.rpc.http

import net.corda.libs.permissions.endpoints.v1.user.UserEndpoint
import net.corda.v5.base.util.contextLogger
import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.platform.commons.util.AnnotationUtils

/**
 * Endpoint availability condition
 *
 * @constructor Create empty Endpoint availability condition
 */
internal class EndpointAvailabilityCondition : ExecutionCondition {

    private companion object {
        val log = contextLogger()
    }

    private val testToolkit by TestToolkitProperty()

    override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult {

        val existingAnnotation =
            AnnotationUtils.findAnnotation(context.element, SkipWhenRpcEndpointUnavailable::class.java)

        if (existingAnnotation.isPresent) {

            if (checkForJenkins()) {
                return ConditionEvaluationResult.enabled("Running under Jenkins CI")
            }

            //check connection here
            return if (checkEndpoint()) {
                ConditionEvaluationResult.enabled("Connection is up")
            } else {
                "HTTP RPC Connection is down or not forwarded, skipping the test".let {
                    log.warn(it)
                    ConditionEvaluationResult.disabled(it)
                }
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
     * Checks to see if HTTP RPC endpoint is reachable
     *
     * @return true if reachable, false if not
     */
    private fun checkEndpoint(): Boolean {
        return try {
            testToolkit.httpClientFor(UserEndpoint::class.java).use { client ->
                val proxy = client.start().proxy
                proxy.protocolVersion > 0
            }
        }  catch (ex: Exception) {
            false
        }
    }
}