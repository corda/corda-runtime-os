package net.corda.sdk.network

import net.corda.membership.rest.v1.MemberRegistrationRestResource
import net.corda.membership.rest.v1.NetworkRestResource
import net.corda.membership.rest.v1.types.request.HostedIdentitySetupRequest
import net.corda.membership.rest.v1.types.request.MemberRegistrationRequest
import net.corda.membership.rest.v1.types.response.RegistrationRequestProgress
import net.corda.membership.rest.v1.types.response.RegistrationStatus
import net.corda.rest.client.RestClient
import net.corda.sdk.rest.RestClientUtils.executeWithRetry
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class RegistrationRequester {

    fun requestRegistration(
        restClient: RestClient<MemberRegistrationRestResource>,
        registrationContext: Map<String, Any?>,
        holdingId: String,
        wait: Duration = 10.seconds
    ): RegistrationRequestProgress {
        val castRegistrationContext: Map<String, String> = registrationContext.mapValues { (_, value) ->
            value.toString()
        }
        val request = MemberRegistrationRequest(
            context = castRegistrationContext,
        )
        return restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Request registration"
            ) {
                val resource = client.start().proxy
                resource.startRegistration(holdingId, request)
            }
        }
    }

    fun waitForRegistrationApproval(
        restClient: RestClient<MemberRegistrationRestResource>,
        registrationId: String,
        holdingId: String,
        wait: Duration = 10.seconds
    ) {
        restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Check registration progress"
            ) {
                val resource = client.start().proxy
                val status = resource.checkSpecificRegistrationProgress(holdingId, registrationId)
                when (val registrationStatus = status.registrationStatus) {
                    RegistrationStatus.APPROVED -> true // Return true to indicate the condition is satisfied
                    RegistrationStatus.DECLINED,
                    RegistrationStatus.INVALID,
                    RegistrationStatus.FAILED,
                    -> throw OnboardException("Status of registration is $registrationStatus; reason: ${status.reason}.")
                    else -> throw OnboardException("Status of registration is $registrationStatus; reason: ${status.reason}.")
                }
            }
        }
    }

    fun registerAndWaitForApproval(
        restClient: RestClient<MemberRegistrationRestResource>,
        registrationContext: Map<String, Any>,
        holdingId: String,
        wait: Duration = 10.seconds
    ) {
        val response = requestRegistration(restClient, registrationContext, holdingId, wait)
        waitForRegistrationApproval(restClient, response.registrationId, holdingId, wait)
    }

    fun configureAsNetworkParticipant(
        restClient: RestClient<NetworkRestResource>,
        request: HostedIdentitySetupRequest,
        holdingId: String,
        wait: Duration = 10.seconds
    ) {
        restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Configure $holdingId as network participant"
            ) {
                val resource = client.start().proxy
                resource.setupHostedIdentities(holdingId, request)
            }
        }
    }
}

internal class OnboardException(message: String) : Exception(message)
