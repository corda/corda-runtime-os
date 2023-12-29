package net.corda.e2etest.utilities.types

import net.corda.e2etest.utilities.CAT_NOTARY
import net.corda.e2etest.utilities.ClusterAInfo.restApiVersion
import net.corda.e2etest.utilities.ClusterInfo
import net.corda.e2etest.utilities.DEFAULT_KEY_SCHEME
import net.corda.e2etest.utilities.DEFAULT_NOTARY_SERVICE
import net.corda.e2etest.utilities.DEFAULT_SIGNATURE_SPEC
import net.corda.e2etest.utilities.addSoftHsmFor
import net.corda.e2etest.utilities.createKeyFor
import net.corda.e2etest.utilities.onboardMember
import net.corda.rest.annotations.RestApiVersion
import net.corda.v5.base.types.MemberX500Name

class DynamicOnboardingGroup(
    val mgm: NetworkOnboardingMetadata,
    val ca: NamedCertificateAuthority,
    val groupPolicy: String,
) {
    @Suppress("LongParameterList")
    fun onboardMember(
        clusterInfo: ClusterInfo,
        cpb: String?,
        cpiName: String,
        x500Name: String,
        waitForApproval: Boolean = true,
        getAdditionalContext: ((holdingId: String) -> Map<String, String>)? = null,
        tlsCertificateUploadedCallback: (String) -> Unit = {},
        useSessionCertificate: Boolean = false,
        useLedgerKey: Boolean = true,
    ): NetworkOnboardingMetadata {
        return clusterInfo.onboardMember(
            ca = ca,
            groupPolicy = groupPolicy,
            cpb = cpb,
            cpiName = cpiName,
            x500Name = x500Name,
            waitForApproval = waitForApproval,
            getAdditionalContext = getAdditionalContext,
            tlsCertificateUploadedCallback = tlsCertificateUploadedCallback,
            useSessionCertificate = useSessionCertificate,
            useLedgerKey = useLedgerKey
        )
    }

    @Suppress("LongParameterList")
    fun onboardNotaryMember(
        clusterInfo: ClusterInfo,
        resourceName: String,
        cpiName: String,
        x500Name: String,
        wait: Boolean = true,
        getAdditionalContext: ((holdingId: String) -> Map<String, String>)? = null,
        tlsCertificateUploadedCallback: (String) -> Unit = {},
        notaryServiceName: String = DEFAULT_NOTARY_SERVICE,
        isBackchainRequired: Boolean = true,
        notaryPlugin: String = "nonvalidating"
    ) : NetworkOnboardingMetadata {
        return onboardMember(
            clusterInfo = clusterInfo,
            cpb = resourceName,
            cpiName = cpiName,
            x500Name,
            wait,
            useLedgerKey = false,
            getAdditionalContext = { holdingId ->
                clusterInfo.addSoftHsmFor(holdingId, CAT_NOTARY)
                val notaryKeyId =
                    clusterInfo.createKeyFor(holdingId, "$holdingId$CAT_NOTARY", CAT_NOTARY, DEFAULT_KEY_SCHEME)

                mapOf(
                    "corda.roles.0" to "notary",
                    "corda.notary.service.name" to MemberX500Name.parse(notaryServiceName).toString(),
                    "corda.notary.service.flow.protocol.name" to "com.r3.corda.notary.plugin.$notaryPlugin",
                    "corda.notary.service.flow.protocol.version.0" to "1",
                    "corda.notary.keys.0.id" to notaryKeyId,
                    "corda.notary.keys.0.signature.spec" to DEFAULT_SIGNATURE_SPEC
                ) + (getAdditionalContext?.let { it(holdingId) } ?: emptyMap()) + (
                        // Add the optional backchain property if version is >= 5.2
                        if (restApiVersion != RestApiVersion.C5_0 && restApiVersion != RestApiVersion.C5_1)
                            mapOf("corda.notary.service.backchain.required" to "$isBackchainRequired")
                        else emptyMap()
                        )
            },
            tlsCertificateUploadedCallback = tlsCertificateUploadedCallback,
        )
    }
}
