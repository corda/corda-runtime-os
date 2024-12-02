package net.corda.sdk.network

import com.fasterxml.jackson.databind.ObjectMapper

val MGM_GROUP_POLICY: String = getMgmGroupPolcy()

private fun getMgmGroupPolcy(): String {
    return mapOf(
        "fileFormatVersion" to 1,
        "groupId" to "CREATE_ID",
        "registrationProtocol" to "net.corda.membership.impl.registration.dynamic.mgm.MGMRegistrationService",
        "synchronisationProtocol" to "net.corda.membership.impl.synchronisation.MgmSynchronisationServiceImpl",
    ).let { groupPolicyMap ->
        ObjectMapper().writeValueAsString(groupPolicyMap)
    }
}
