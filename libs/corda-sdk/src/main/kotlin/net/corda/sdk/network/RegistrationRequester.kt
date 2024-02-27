package net.corda.sdk.network

import net.corda.membership.rest.v1.MemberRegistrationRestResource
import net.corda.membership.rest.v1.NetworkRestResource
import net.corda.membership.rest.v1.types.request.HostedIdentitySetupRequest
import net.corda.membership.rest.v1.types.request.MemberRegistrationRequest
import net.corda.membership.rest.v1.types.response.RegistrationRequestProgress
import net.corda.membership.rest.v1.types.response.RegistrationStatus
import net.corda.rest.client.RestClient
import net.corda.sdk.rest.InvariantUtils

class RegistrationRequester {

    fun requestRegistration(
        restClient: RestClient<MemberRegistrationRestResource>,
        registrationContext: Map<String, Any?>,
        holdingId: String
    ): RegistrationRequestProgress {
        val castRegistrationContext: Map<String, String> = registrationContext.mapValues { (_, value) ->
            value.toString()
        }
        val request = MemberRegistrationRequest(
            context = castRegistrationContext,
        )
        return restClient.use { client ->
            InvariantUtils.checkInvariant(
                errorMessage = "Failed to request registration after ${InvariantUtils.MAX_ATTEMPTS} attempts."
            ) {
                try {
                    val resource = client.start().proxy
                    resource.startRegistration(holdingId, request)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    fun waitForRegistrationApproval(
        restClient: RestClient<MemberRegistrationRestResource>,
        registrationId: String,
        holdingId: String
    ) {
        restClient.use { client ->
            InvariantUtils.checkInvariant(
                errorMessage = "Check Registration Progress failed after ${InvariantUtils.MAX_ATTEMPTS} attempts."
            ) {
                try {
                    val resource = client.start().proxy
                    val status = resource.checkSpecificRegistrationProgress(holdingId, registrationId)
                    when (val registrationStatus = status.registrationStatus) {
                        RegistrationStatus.APPROVED -> true // Return true to indicate the invariant is satisfied
                        RegistrationStatus.DECLINED,
                        RegistrationStatus.INVALID,
                        RegistrationStatus.FAILED,
                        -> throw OnboardException("Status of registration is $registrationStatus.")

                        else -> {
                            println("Status of registration is $registrationStatus")
                            null
                        }
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    fun registerAndWaitForApproval(
        restClient: RestClient<MemberRegistrationRestResource>,
        registrationContext: Map<String, Any>,
        holdingId: String
    ) {
        val response = requestRegistration(restClient, registrationContext, holdingId)
        waitForRegistrationApproval(restClient, response.registrationId, holdingId)
    }

    fun configureAsNetworkParticipant(restClient: RestClient<NetworkRestResource>, request: HostedIdentitySetupRequest, holdingId: String) {
        restClient.use { client ->
            InvariantUtils.checkInvariant(
                errorMessage = "Unable to configure $holdingId as network participant after ${InvariantUtils.MAX_ATTEMPTS} attempts."
            ) {
                try {
                    val resource = client.start().proxy
                    resource.setupHostedIdentities(holdingId, request)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
}

internal class OnboardException(message: String) : Exception(message)
