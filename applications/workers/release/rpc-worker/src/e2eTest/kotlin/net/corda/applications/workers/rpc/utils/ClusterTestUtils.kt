package net.corda.applications.workers.rpc.utils

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.cli.plugins.packaging.CreateCpiV2
import net.corda.cli.plugins.packaging.signing.SigningOptions
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.httprpc.HttpFileUpload
import net.corda.httprpc.JsonObject
import net.corda.httprpc.client.exceptions.RequestErrorException
import net.corda.libs.configuration.endpoints.v1.ConfigRestResource
import net.corda.libs.configuration.endpoints.v1.types.ConfigSchemaVersion
import net.corda.libs.configuration.endpoints.v1.types.UpdateConfigParameters
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource
import net.corda.libs.virtualnode.endpoints.v1.VirtualNodeRestResource
import net.corda.libs.virtualnode.endpoints.v1.types.VirtualNodeRequest
import net.corda.libs.packaging.testutils.TestUtils
import net.corda.membership.rest.v1.CertificatesRestResource
import net.corda.membership.rest.v1.HsmRestResource
import net.corda.membership.rest.v1.KeysRestResource
import net.corda.membership.rest.v1.MGMRestResource
import net.corda.membership.rest.v1.MemberRegistrationRestResource
import net.corda.membership.rest.v1.NetworkRestResource
import net.corda.membership.rest.v1.types.request.HostedIdentitySetupRequest
import net.corda.membership.rest.v1.types.request.MemberRegistrationRequest
import net.corda.membership.rest.v1.types.response.HsmAssociationInfo
import net.corda.membership.rest.v1.types.response.RegistrationRequestProgress
import net.corda.membership.rest.v1.types.response.RegistrationStatus
import net.corda.test.util.eventually
import net.corda.v5.base.util.minutes
import net.corda.v5.base.util.seconds
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.fail
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID

const val GATEWAY_CONFIG = "corda.p2p.gateway"
const val LINK_MANAGER_CONFIG = "corda.p2p.linkManager"
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
    val testRunUniqueId = UUID.randomUUID()
    val keystore = TestUtils.ROOT_CA_KEY_STORE
    val keyStoreFilePath = Path.of(tempDir.toString(), "rootca-$testRunUniqueId.p12")

    Files.newOutputStream(
        keyStoreFilePath,
        StandardOpenOption.WRITE,
        StandardOpenOption.CREATE_NEW
    ).write(keystore.readAllBytes())

    // first upload certificate to corda
    clusterHttpClientFor(CertificatesRestResource::class.java).use { client ->
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
    return clusterHttpClientFor(CpiUploadRestResource::class.java).use { client ->
        with(client.start().proxy) {
            // Check if MGM CPI was already uploaded in previous run. Current validation only allows one MGM CPI.
            if (isMgm) {
                getAllCpis().cpis.firstOrNull {
                    it.groupPolicy?.contains("CREATE_ID") ?: false
                }?.let {
                    return it.cpiFileChecksum
                }
            }

            val groupPolicyFilePath = Path.of(tempDir.toString(), "groupPolicy-$testRunUniqueId.json")

            Files.newOutputStream(
                groupPolicyFilePath,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE_NEW
            ).write(groupPolicy)

            val cpiFileName = Path.of(tempDir.toString(), "test-$testRunUniqueId.cpi")
            CreateCpiV2().apply {
                outputFileName = cpiFileName.toString()
                cpiName = "test-cpi_$testRunUniqueId"
                cpiVersion = "1.0.0.0-SNAPSHOT"
                cpiUpgrade = false
                groupPolicyFileName = groupPolicyFilePath.toString()
                signingOptions = SigningOptions().apply {
                    keyStoreFileName = keyStoreFilePath.toString()
                    keyStorePass = TestUtils.KEY_STORE_PASSWORD.concatToString()
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
    clusterHttpClientFor(VirtualNodeRestResource::class.java)
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
    return clusterHttpClientFor(KeysRestResource::class.java)
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
    return clusterHttpClientFor(KeysRestResource::class.java)
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
    return clusterHttpClientFor(HsmRestResource::class.java)
        .use { client ->
            client.start().proxy.assignSoftHsm(holdingId, cat)
        }
}

fun E2eCluster.register(
    holdingId: String,
    context: Map<String, String>
): RegistrationRequestProgress {
    return clusterHttpClientFor(MemberRegistrationRestResource::class.java)
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
    return clusterHttpClientFor(MGMRestResource::class.java).use { client ->
        client.start().proxy.generateGroupPolicy(holdingId)
    }
}

fun E2eCluster.setUpNetworkIdentity(
    holdingId: String,
    sessionKeyId: String,
    useClusterLevelSessionKeyAndCert: Boolean? = null,
    sessionCertificateChainAlias: String? = null
) {
    clusterHttpClientFor(NetworkRestResource::class.java).use { client ->
        client.start().proxy.setupHostedIdentities(
            holdingId,
            HostedIdentitySetupRequest(
                p2pTlsCertificateChainAlias = TLS_CERT_ALIAS,
                useClusterLevelTlsCertificateAndKey = true,
                useClusterLevelSessionCertificateAndKey = useClusterLevelSessionKeyAndCert,
                sessionKeyId = sessionKeyId,
                sessionCertificateChainAlias = sessionCertificateChainAlias
            )
        )
    }
}

fun E2eCluster.disableGatewayCLRChecks() {
    val sslConfig = "sslConfig"
    val revocationCheck = "revocationCheck"
    val mode = "mode"
    val modeOff = "OFF"
    clusterHttpClientFor(ConfigRestResource::class.java).use { client ->
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
                    TestJsonObject("{ \"$sslConfig\": { \"$revocationCheck\": { \"$mode\": \"$modeOff\" }  } }"),
                    ConfigSchemaVersion(1, 0)
                )
            )
        }
    }
}

fun E2eCluster.disableLinkManagerCLRChecks() {
    val revocationCheck = "revocationCheck"
    val mode = "mode"
    val modeOff = "OFF"
    clusterHttpClientFor(ConfigRestResource::class.java).use { client ->
        val proxy = client.start().proxy
        val configResponse = proxy.get(LINK_MANAGER_CONFIG)
        val config = ObjectMapper().readTree(
            configResponse.configWithDefaults
        )
        if (modeOff != config[revocationCheck][mode].asText()) {
            proxy.updateConfig(
                UpdateConfigParameters(
                    LINK_MANAGER_CONFIG,
                    configResponse.version,
                    TestJsonObject("{ \"$revocationCheck\": { \"$mode\": \"$modeOff\" } }"),
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
    tempDir: Path,
    useSessionCertificate: Boolean = false,
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

        if (useSessionCertificate) {
            val memberSessionCsr = generateCsr(member, memberSessionKeyId, member.holdingId, addHostToSubjectAlternativeNames = false)
            val memberSessionCert = getCa().generateCert(memberSessionCsr)
            uploadSessionCertificate(memberSessionCert, member.holdingId)
        }

        val memberLedgerKeyId = generateKeyPairIfNotExists(member.holdingId, HSM_CAT_LEDGER)

        if (useSessionCertificate) {
            setUpNetworkIdentity(member.holdingId, memberSessionKeyId, useClusterLevelSessionKeyAndCert = false, SESSION_CERT_ALIAS)
        } else {
            setUpNetworkIdentity(member.holdingId, memberSessionKeyId)
        }


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
    tempDir: Path,
    useSessionCertificate: Boolean = false,
) {
    val cpiChecksum = uploadCpi(createMGMGroupPolicyJson(), tempDir, true)
    createVirtualNode(mgm, cpiChecksum)
    assignSoftHsm(mgm.holdingId, HSM_CAT_SESSION)
    assignSoftHsm(mgm.holdingId, HSM_CAT_PRE_AUTH)

    val mgmSessionKeyId = generateKeyPairIfNotExists(mgm.holdingId, HSM_CAT_SESSION)
    val mgmECDHKeyId = generateKeyPairIfNotExists(mgm.holdingId, HSM_CAT_PRE_AUTH)

    val mgmRegistrationContext = if (useSessionCertificate) {
        val mgmSessionCsr = generateCsr(mgm, mgmSessionKeyId, mgm.holdingId, addHostToSubjectAlternativeNames = false)
        val mgmSessionCert = getCa().generateCert(mgmSessionCsr)
        uploadSessionCertificate(mgmSessionCert, mgm.holdingId)
        createMgmRegistrationContext(
            caTrustRoot = getCa().caCertificate.toPem(),
            sessionKeyId = mgmSessionKeyId,
            ecdhKeyId = mgmECDHKeyId,
            p2pUrl = p2pUrl,
            sessionPkiMode = "Standard"
        )
    } else {
        createMgmRegistrationContext(
            caTrustRoot = getCa().caCertificate.toPem(),
            sessionKeyId = mgmSessionKeyId,
            ecdhKeyId = mgmECDHKeyId,
            p2pUrl = p2pUrl
        )
    }

    register(
        mgm.holdingId,
        mgmRegistrationContext,
    )

    assertOnlyMgmIsInMemberList(mgm.holdingId, mgm.name)

    if (!keyExists(P2P_TENANT_ID, HSM_CAT_TLS)) {
        val mgmTlsKeyId = generateKeyPairIfNotExists(P2P_TENANT_ID, HSM_CAT_TLS)
        val mgmTlsCsr = generateCsr(mgm, mgmTlsKeyId)
        val mgmTlsCert = getCa().generateCert(mgmTlsCsr)
        uploadTlsCertificate(mgmTlsCert)
    }

    if (useSessionCertificate) {
        setUpNetworkIdentity(mgm.holdingId, mgmSessionKeyId, useClusterLevelSessionKeyAndCert = false, SESSION_CERT_ALIAS)
    } else {
        setUpNetworkIdentity(mgm.holdingId, mgmSessionKeyId)
    }
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
