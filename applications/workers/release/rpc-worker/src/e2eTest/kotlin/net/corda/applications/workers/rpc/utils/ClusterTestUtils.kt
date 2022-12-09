package net.corda.applications.workers.rpc.utils

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.cli.plugins.packaging.CreateCpiV2
import net.corda.cli.plugins.packaging.signing.SigningOptions
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.httprpc.HttpFileUpload
import net.corda.httprpc.JsonObject
import net.corda.httprpc.client.exceptions.RequestErrorException
import net.corda.libs.configuration.endpoints.v1.ConfigRPCOps
import net.corda.libs.configuration.endpoints.v1.types.ConfigSchemaVersion
import net.corda.libs.configuration.endpoints.v1.types.UpdateConfigParameters
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRPCOps
import net.corda.libs.virtualnode.endpoints.v1.VirtualNodeRPCOps
import net.corda.libs.virtualnode.endpoints.v1.types.VirtualNodeRequest
import net.corda.libs.packaging.testutils.TestUtils
import net.corda.membership.httprpc.v1.CertificatesRpcOps
import net.corda.membership.httprpc.v1.HsmRpcOps
import net.corda.membership.httprpc.v1.KeysRpcOps
import net.corda.membership.httprpc.v1.MGMRpcOps
import net.corda.membership.httprpc.v1.MemberRegistrationRpcOps
import net.corda.membership.httprpc.v1.NetworkRpcOps
import net.corda.membership.httprpc.v1.types.request.HostedIdentitySetupRequest
import net.corda.membership.httprpc.v1.types.request.MemberRegistrationRequest
import net.corda.membership.httprpc.v1.types.response.HsmAssociationInfo
import net.corda.membership.httprpc.v1.types.response.RegistrationRequestProgress
import net.corda.membership.httprpc.v1.types.response.RegistrationStatus
import net.corda.test.util.eventually
import net.corda.v5.base.util.minutes
import net.corda.v5.base.util.seconds
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*

const val GATEWAY_CONFIG = "corda.p2p.gateway"
const val P2P_TENANT_ID = "p2p"
const val HSM_CAT_SESSION = "SESSION_INIT"
const val HSM_CAT_PRE_AUTH = "PRE_AUTH"
const val HSM_CAT_LEDGER = "LEDGER"
const val HSM_CAT_TLS = "TLS"

private data class TestJsonObject(override val escapedJson: String = "") : JsonObject

fun E2eCluster.uploadCpi(
    groupPolicy: ByteArray,
    tempDir: Path,
    isMgm: Boolean = false
): String {
    val keyStoreFilePath = Path.of(tempDir.toString(), "rootca.p12")
    val keystore = TestUtils::class.java.classLoader.getResourceAsStream("rootca.p12")

    Files.newOutputStream(
        keyStoreFilePath,
        StandardOpenOption.WRITE,
        StandardOpenOption.CREATE_NEW
    ).write(keystore.readAllBytes())

    // first upload certificate to corda
    clusterHttpClientFor(CertificatesRpcOps::class.java).use { client ->
        with(client.start().proxy) {
            val pem = TestUtils.ROOT_CA.toPem().toByteArray()

            val upload = HttpFileUpload(
                content = pem.inputStream(),
                contentType = "application/x-pem-file",
                extension = "pem",
                fileName = "rootca.pem",
                size = pem.size.toLong()
            )

            importCertificateChain("code-signer", "rootca", listOf(upload))
        }
    }

    // then build and upload cpi
    return clusterHttpClientFor(CpiUploadRPCOps::class.java).use { client ->
        with(client.start().proxy) {
            // Check if MGM CPI was already uploaded in previous run. Current validation only allows one MGM CPI.
            if (isMgm) {
                getAllCpis().cpis.firstOrNull {
                    it.groupPolicy?.contains("CREATE_ID") ?: false
                }?.let {
                    return it.cpiFileChecksum
                }
            }

            val groupPolicyFilePath = Path.of(tempDir.toString(), "groupPolicy.json")

            Files.newOutputStream(
                groupPolicyFilePath,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE_NEW
            ).write(groupPolicy)

            val testRunUniqueId = UUID.randomUUID()
            val applicationCpiName = "test-cpi_$testRunUniqueId"

            val cpiFileName = Path.of(tempDir.toString(), "test.cpi")
            CreateCpiV2().apply {
                cpbFileName = ""
                outputFileName = cpiFileName.toString()
                cpiName = applicationCpiName
                cpiVersion = "1.0.0.0-SNAPSHOT"
                cpiUpgrade = false
                groupPolicyFileName = groupPolicyFilePath.toString()
                signingOptions = SigningOptions().apply {
                    keyStoreFileName = keyStoreFilePath.toString()
                    keyStorePass = "cordadevpass"
                    keyAlias = "rootca"
                }
            }.run()

            val cpiJar = Files.readAllBytes(cpiFileName)

            val upload = HttpFileUpload(
                content = cpiJar.inputStream(),
                contentType = "application/java-archive",
                extension = "cpb",
                fileName = "${uniqueName}.cpb",
                size = cpiJar.size.toLong(),
            )
            val id = cpi(upload).id
            eventually {
                val status = try {
                    // status() throws exceptions for certain Http errors rather than returning an error. This means we
                    // must catch any errors expected due to asynchronicity here and fail them, so the eventually loop
                    // can retry rather than stop the test with an unexpected exception at this point.
                    status(id)
                } catch(requestErrorException: RequestErrorException) {
                    fail(requestErrorException)
                }
                assertThat(status.status).isEqualTo("OK")
                status.cpiFileChecksum
            }
        }
    }
}

fun E2eCluster.createVirtualNode(
    member: E2eClusterMember,
    cpiCheckSum: String
) {
    clusterHttpClientFor(VirtualNodeRPCOps::class.java)
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
): Boolean {
    return clusterHttpClientFor(KeysRpcOps::class.java)
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
): String {
    return clusterHttpClientFor(KeysRpcOps::class.java)
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
                        ECDSA_SECP256R1_CODE_NAME
                    ).id
            }
        }
}

fun E2eCluster.assignSoftHsm(
    holdingId: String,
    cat: String
): HsmAssociationInfo {
    return clusterHttpClientFor(HsmRpcOps::class.java)
        .use { client ->
            client.start().proxy.assignSoftHsm(holdingId, cat)
        }
}

fun E2eCluster.register(
    holdingId: String,
    context: Map<String, String>
): RegistrationRequestProgress {
    return clusterHttpClientFor(MemberRegistrationRpcOps::class.java)
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

                eventually(duration = 1.minutes) {
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
): String {
    return clusterHttpClientFor(MGMRpcOps::class.java).use { client ->
        client.start().proxy.generateGroupPolicy(holdingId)
    }
}

fun E2eCluster.setUpNetworkIdentity(
    holdingId: String,
    sessionKeyId: String
) {
    clusterHttpClientFor(NetworkRpcOps::class.java).use { client ->
        client.start().proxy.setupHostedIdentities(
            holdingId,
            HostedIdentitySetupRequest(
                TLS_CERT_ALIAS,
                true,
                null,
                sessionKeyId
            )
        )
    }
}

fun E2eCluster.disableCLRChecks() {
    val sslConfig = "sslConfig"
    val revocationCheck = "revocationCheck"
    val mode = "mode"
    val modeOff = "OFF"
    clusterHttpClientFor(ConfigRPCOps::class.java).use { client ->
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
                    TestJsonObject("{ \"$sslConfig\": { \"$revocationCheck\": { \"$mode\": \"$modeOff\" }  }  }"),
                    ConfigSchemaVersion(1, 0)
                )
            )
        }
    }
}

/**
 * Onboard all members in a cluster definition using a given CPI checksum.
 * Returns a map from member X500 name to holding ID.
 */
fun E2eCluster.onboardMembers(
    mgm: E2eClusterMember,
    memberGroupPolicy: String,
    @TempDir tempDir: Path
): List<E2eClusterMember> {
    val holdingIds = mutableListOf<E2eClusterMember>()
    val memberCpiChecksum = uploadCpi(memberGroupPolicy.toByteArray(), tempDir)
    members.forEach { member ->
        createVirtualNode(member, memberCpiChecksum)

        assignSoftHsm(member.holdingId, HSM_CAT_SESSION)
        assignSoftHsm(member.holdingId, HSM_CAT_LEDGER)

        if (!keyExists(P2P_TENANT_ID, HSM_CAT_TLS)) {
            val memberTlsKeyId = generateKeyPairIfNotExists(P2P_TENANT_ID, HSM_CAT_TLS)
            val memberTlsCsr = generateCsr(member, memberTlsKeyId)
            val memberTlsCert = getCa().generateCert(memberTlsCsr)
            uploadTlsCertificate(memberTlsCert)
        }

        val memberSessionKeyId = generateKeyPairIfNotExists(member.holdingId, HSM_CAT_SESSION)
        val memberLedgerKeyId = generateKeyPairIfNotExists(member.holdingId, HSM_CAT_LEDGER)

        setUpNetworkIdentity(member.holdingId, memberSessionKeyId)

        assertOnlyMgmIsInMemberList(member.holdingId, mgm.name)
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
        assertMemberInMemberList(
            member.holdingId,
            member
        )
    }
    return holdingIds
}

fun E2eCluster.onboardMgm(
    mgm: E2eClusterMember,
    @TempDir tempDir: Path
) {
    val cpiChecksum = uploadCpi(createMGMGroupPolicyJson(), tempDir, true)
    createVirtualNode(mgm, cpiChecksum)
    assignSoftHsm(mgm.holdingId, HSM_CAT_SESSION)
    assignSoftHsm(mgm.holdingId, HSM_CAT_PRE_AUTH)

    val mgmSessionKeyId = generateKeyPairIfNotExists(mgm.holdingId, HSM_CAT_SESSION)
    val mgmECDHKeyId = generateKeyPairIfNotExists(mgm.holdingId, HSM_CAT_PRE_AUTH)

    register(
        mgm.holdingId,
        createMgmRegistrationContext(
            tlsTrustRoot = getCa().caCertificate.toPem(),
            sessionKeyId = mgmSessionKeyId,
            ecdhKeyId = mgmECDHKeyId,
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

fun E2eCluster.onboardStaticMembers(groupPolicy: ByteArray, tempDir: Path) {
    val cpiCheckSum = uploadCpi(groupPolicy, tempDir)

    members.forEach { member ->
        createVirtualNode(member, cpiCheckSum)

        register(
            member.holdingId,
            mapOf(
                "corda.key.scheme" to ECDSA_SECP256R1_CODE_NAME
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
                .hasSize(allMembers.size)
                .allSatisfy { memberInfo ->
                    assertThat(memberInfo.status).isEqualTo("ACTIVE")
                    assertThat(memberInfo.groupId).isEqualTo(groupId)
                }
            assertThat(result.map { memberInfo -> memberInfo.name })
                .hasSize(allMembers.size)
                .containsExactlyInAnyOrderElementsOf(expectedList)
        }
    }
}
