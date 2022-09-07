package net.corda.applications.workers.rpc.utils

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.httprpc.HttpFileUpload
import net.corda.libs.configuration.endpoints.v1.ConfigRPCOps
import net.corda.libs.configuration.endpoints.v1.types.ConfigSchemaVersion
import net.corda.libs.configuration.endpoints.v1.types.UpdateConfigParameters
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRPCOps
import net.corda.libs.virtualnode.endpoints.v1.VirtualNodeRPCOps
import net.corda.libs.virtualnode.endpoints.v1.types.VirtualNodeRequest
import net.corda.membership.httprpc.v1.HsmRpcOps
import net.corda.membership.httprpc.v1.KeysRpcOps
import net.corda.membership.httprpc.v1.MGMRpcOps
import net.corda.membership.httprpc.v1.MemberRegistrationRpcOps
import net.corda.membership.httprpc.v1.NetworkRpcOps
import net.corda.membership.httprpc.v1.types.request.HostedIdentitySetupRequest
import net.corda.membership.httprpc.v1.types.request.MemberRegistrationRequest
import net.corda.membership.httprpc.v1.types.response.RegistrationStatus
import net.corda.test.util.eventually
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.minutes
import net.corda.v5.base.util.seconds
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import java.io.File

const val GATEWAY_CONFIG = "corda.p2p.gateway"
const val KEY_SCHEME = "CORDA.ECDSA.SECP256R1"
const val P2P_TENANT_ID = "p2p"
const val HSM_CAT_SESSION = "SESSION_INIT"
const val HSM_CAT_LEDGER = "LEDGER"
const val HSM_CAT_TLS = "TLS"

fun E2eCluster.uploadCpi(
    groupPolicy: ByteArray,
    isMgm: Boolean = false
) = with(testToolkit) {
    httpClientFor(CpiUploadRPCOps::class.java).use { client ->
        with(client.start().proxy) {
            // Check if MGM CPI was already uploaded in previous run. Current validation only allows one MGM CPI.
            if (isMgm) {
                getAllCpis().cpis.firstOrNull {
                    it.groupPolicy?.contains("CREATE_ID") ?: false
                }?.let {
                    return it.cpiFileChecksum
                }
            }

            val jar = createEmptyJarWithManifest(groupPolicy)
            val upload = HttpFileUpload(
                content = jar.inputStream(),
                contentType = "application/java-archive",
                extension = "cpb",
                fileName = "$uniqueName.cpb",
                size = jar.size.toLong(),
            )
            val id = cpi(upload).id
            eventually {
                val status = status(id)
                assertThat(status.status).isEqualTo("OK")
                status.cpiFileChecksum
            }
        }
    }
}

fun E2eCluster.createVirtualNode(
    member: E2eClusterMember,
    cpiCheckSum: String
) = with(testToolkit) {
    httpClientFor(VirtualNodeRPCOps::class.java)
        .use { client ->
            client.start().proxy.createVirtualNode(
                VirtualNodeRequest(
                    x500Name = member.name,
                    cpiFileChecksum = cpiCheckSum,
                    vaultDdlConnection = null,
                    vaultDmlConnection = null,
                    cryptoDdlConnection = null,
                    cryptoDmlConnection = null,
                    uniquenessDdlConnection = null,
                    uniquenessDmlConnection = null
                )
            ).holdingIdentity.shortHash.also {
                member.holdingId = it
            }
        }
}

fun E2eCluster.keyExists(
    tenantId: String,
    cat: String
) = with(testToolkit) {
    httpClientFor(KeysRpcOps::class.java)
        .use { client ->
            with(client.start().proxy) {
                val keyAlias = "$tenantId-$cat"
                listKeys(
                    tenantId = tenantId,
                    skip = 0,
                    take = 1,
                    orderBy = "none",
                    category = cat,
                    schemeCodeName = null,
                    alias = keyAlias,
                    masterKeyAlias = null,
                    createdAfter = null,
                    createdBefore = null,
                    ids = null
                ).isNotEmpty()
            }
        }
}

fun E2eCluster.generateKeyPairIfNotExists(
    tenantId: String,
    cat: String
) = with(testToolkit) {
    httpClientFor(KeysRpcOps::class.java)
        .use { client ->
            with(client.start().proxy) {
                val keyAlias = "$tenantId-$cat"
                listKeys(
                    tenantId = tenantId,
                    skip = 0,
                    take = 1,
                    orderBy = "none",
                    category = cat,
                    schemeCodeName = null,
                    alias = keyAlias,
                    masterKeyAlias = null,
                    createdAfter = null,
                    createdBefore = null,
                    ids = null
                ).map {
                    it.value
                }.firstOrNull()
                    ?.keyId
                    ?: generateKeyPair(
                        tenantId,
                        keyAlias,
                        cat,
                        KEY_SCHEME
                    )
            }
        }
}

fun E2eCluster.assignSoftHsm(
    holdingId: String,
    cat: String
) = with(testToolkit) {
    httpClientFor(HsmRpcOps::class.java)
        .use { client ->
            client.start().proxy.assignSoftHsm(holdingId, cat)
        }
}

fun E2eCluster.register(
    holdingId: String,
    context: Map<String, String>
) = with(testToolkit) {
    httpClientFor(MemberRegistrationRpcOps::class.java)
        .use { client ->
            val proxy = client.start().proxy
            proxy.startRegistration(
                holdingId,
                MemberRegistrationRequest(
                    action = "requestJoin",
                    context = context
                )
            ).apply {
                assertThat(registrationStatus).isEqualTo("SUBMITTED")

                eventually(duration = 5.minutes, waitBefore = 1.seconds) {
                    val registrationStatus = proxy.checkSpecificRegistrationProgress(holdingId, registrationId)
                    assertThat(registrationStatus?.registrationStatus)
                        .isNotNull
                        .isEqualTo(RegistrationStatus.APPROVED)
                }
            }
        }
}

fun E2eCluster.generateGroupPolicy(
    holdingId: String
) = with(testToolkit) {
    httpClientFor(MGMRpcOps::class.java).use { client ->
        client.start().proxy.generateGroupPolicy(holdingId)
    }
}

fun E2eCluster.setUpNetworkIdentity(
    holdingId: String,
    sessionKeyId: String
) = with(testToolkit) {
    httpClientFor(NetworkRpcOps::class.java).use { client ->
        client.start().proxy.setupHostedIdentities(
            holdingId,
            HostedIdentitySetupRequest(
                TLS_CERT_ALIAS,
                P2P_TENANT_ID,
                null,
                sessionKeyId
            )
        )
    }
}

fun E2eCluster.disableCLRChecks() = with(testToolkit) {
    val sslConfig = "sslConfig"
    val revocationCheck = "revocationCheck"
    val mode = "mode"
    val modeOff = "OFF"
    httpClientFor(ConfigRPCOps::class.java).use { client ->
        val proxy = client.start().proxy
        val configResponse = proxy.get(GATEWAY_CONFIG)
        val config = ObjectMapper().readTree(
            configResponse.configWithDefaults
        )
        if (modeOff != config[sslConfig][revocationCheck][mode].asText()) {
            proxy.updateConfig(
                UpdateConfigParameters(
                    GATEWAY_CONFIG,
                    configResponse.version,
                    "{ \"$sslConfig\": { \"$revocationCheck\": { \"$mode\": \"$modeOff\" }  }  }",
                    ConfigSchemaVersion(1, 0)
                )
            )
        }
    }
}

fun exec(
    dir: File,
    command: String,
    vararg args: String
): String {
    val all = listOf(command) + args
    println("executeing :$all")
    val process = ProcessBuilder(listOf(command) + args)
        .directory(dir)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .redirectInput(ProcessBuilder.Redirect.INHERIT)
        .start()
    val output = process.inputStream.reader().readText()
    if (process.waitFor() != 0) {
        println(process.errorStream.reader().readText())
        throw CordaRuntimeException("Could not run process: $all")
    }
    return output
}

/**
 * Onboard all members in a cluster definition using a given CPI checksum.
 * Returns a map from member X500 name to holding ID.
 */
fun E2eCluster.onboardMembers(
    mgm: E2eClusterMember,
    memberGroupPolicy: String? = null,
    cpiHash: String? = null
): List<E2eClusterMember> {
    println("onloading members to ${this.clusterConfig.clusterName}")
    val holdingIds = mutableListOf<E2eClusterMember>()
    val memberCpiChecksum = cpiHash
        ?: memberGroupPolicy?.let{
            uploadCpi(it.toByteArray())
        } ?: fail("Need to specify cpiHash or provide a group policy file.")
    val exists = testToolkit.httpClientFor(VirtualNodeRPCOps::class.java)
        .use { client ->
            client.start().proxy.getAllVirtualNodes().virtualNodes.associate {
                MemberX500Name.parse(it.holdingIdentity.x500Name) to it.holdingIdentity.shortHash
            }
        }
    members
        .filter { !it.isMgm }
        .filter {
            val hash = exists[MemberX500Name.parse(it.name)]
            if(hash != null) {
                it.holdingId = hash
                println("Skiping ${it.name}")
                false
            } else {
                true
            }
        }
        .forEach { member ->

            println("Onboarding ${member.name}")
            println("\t createVirtualNode")
            createVirtualNode(member, memberCpiChecksum)

            println("\t assign keys")
            assignSoftHsm(member.holdingId, HSM_CAT_SESSION)
            assignSoftHsm(member.holdingId, HSM_CAT_LEDGER)

            if (!keyExists(P2P_TENANT_ID, HSM_CAT_TLS)) {
                println("\t upload TLS..")
                val memberTlsKeyId = generateKeyPairIfNotExists(P2P_TENANT_ID, HSM_CAT_TLS)
                val memberTlsCsr = generateCsr(member, memberTlsKeyId)
                val memberTlsCert = getCa().generateCert(memberTlsCsr)
                uploadTlsCertificate(memberTlsCert)
            }

            println("\t generate key pairs")
            val memberSessionKeyId = generateKeyPairIfNotExists(member.holdingId, HSM_CAT_SESSION)
            val memberLedgerKeyId = generateKeyPairIfNotExists(member.holdingId, HSM_CAT_LEDGER)

            println("\t setup network identitys")
            setUpNetworkIdentity(member.holdingId, memberSessionKeyId)

            assertOnlyMgmIsInMemberList(member.holdingId, mgm.name)
            println("\t register")
            register(
                member.holdingId,
                createMemberRegistrationContext(
                    this,
                    memberSessionKeyId,
                    memberLedgerKeyId
                )
            )

            // Check registration complete.
            // Eventually we can use the registration status endpoint.
            // For now just assert we have received our own member data.
            println("\t assertMemberInMemberList")
            assertMemberInMemberList(
                member.holdingId,
                member
            )
            println("Onboared ${member.name}")
    }
    return holdingIds
}

fun E2eCluster.onboardMgm(
    mgm: E2eClusterMember,
    cpiHash: String? = null
) {
    val cpiChecksum = cpiHash ?: uploadCpi(createMGMGroupPolicyJson(), true)
    createVirtualNode(mgm, cpiChecksum)
    assignSoftHsm(mgm.holdingId, HSM_CAT_SESSION)

    val mgmSessionKeyId = generateKeyPairIfNotExists(mgm.holdingId, HSM_CAT_SESSION)
    println("mgmSessionKeyId - $mgmSessionKeyId")
    Thread.sleep(300)

    register(
        mgm.holdingId,
        createMgmRegistrationContext(
            tlsTrustRoot = getCa().caCertificate.toPem(),
            sessionKeyId = mgmSessionKeyId,
            p2pUrl = p2pUrl
        )
    )

    assertOnlyMgmIsInMemberList(mgm.holdingId, mgm.name)

    if (!keyExists(P2P_TENANT_ID, HSM_CAT_TLS)) {
        val mgmTlsKeyId = generateKeyPairIfNotExists(P2P_TENANT_ID, HSM_CAT_TLS)
        val mgmTlsCsr = generateCsr(mgm, mgmTlsKeyId)
        val mgmTlsCert = getCa().generateCert(mgmTlsCsr)
        uploadTlsCertificate(mgmTlsCert)
    }

    setUpNetworkIdentity(mgm.holdingId, mgmSessionKeyId)
}

fun E2eCluster.onboardStaticMembers(groupPolicy: ByteArray) {
    val cpiCheckSum = uploadCpi(groupPolicy)

    members.forEach { member ->
        createVirtualNode(member, cpiCheckSum)

        register(
            member.holdingId,
            mapOf(
                "corda.key.scheme" to KEY_SCHEME
            )
        )

        // Check registration complete.
        // Eventually we can use the registration status endpoint.
        // For now just assert we have received our own member data.
        assertMemberInMemberList(
            member.holdingId,
            member
        )
    }
}

fun E2eCluster.assertAllMembersAreInMemberList(
    member: E2eClusterMember,
    allMembers: List<E2eClusterMember>
) {
    eventually(
        waitBetween = 2.seconds,
        duration = 60.seconds
    ) {
        val groupId = getGroupId(member.holdingId)
        lookupMembers(member.holdingId).also { result ->
            val expectedList = allMembers.map { member -> member.name }
            assertThat(result)
                //.hasSize(allMembers.size)
                .allSatisfy { memberInfo ->
                    assertThat(memberInfo.status).isEqualTo("ACTIVE")
                    assertThat(memberInfo.groupId).isEqualTo(groupId)
                }
            assertThat(result.map { memberInfo -> memberInfo.name })
                //.hasSize(allMembers.size)
                .containsExactlyInAnyOrderElementsOf(expectedList)
        }
    }
}
