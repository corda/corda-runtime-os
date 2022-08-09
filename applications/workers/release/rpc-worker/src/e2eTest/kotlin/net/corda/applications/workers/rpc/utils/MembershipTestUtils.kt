package net.corda.applications.workers.rpc.utils

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.applications.workers.rpc.http.TestToolkit
import net.corda.applications.workers.rpc.kafka.KafkaTestToolKit
import net.corda.crypto.test.certificates.generation.CertificateAuthority
import net.corda.crypto.test.certificates.generation.CertificateAuthorityFactory
import net.corda.crypto.test.certificates.generation.FileSystemCertificatesAuthority
import net.corda.crypto.test.certificates.generation.toFactoryDefinitions
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.data.identity.HoldingIdentity
import net.corda.httprpc.HttpFileUpload
import net.corda.libs.configuration.endpoints.v1.ConfigRPCOps
import net.corda.libs.configuration.endpoints.v1.types.ConfigSchemaVersion
import net.corda.libs.configuration.endpoints.v1.types.UpdateConfigParameters
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRPCOps
import net.corda.libs.virtualnode.endpoints.v1.VirtualNodeRPCOps
import net.corda.libs.virtualnode.endpoints.v1.types.VirtualNodeRequest
import net.corda.membership.httprpc.v1.CertificatesRpcOps
import net.corda.membership.httprpc.v1.HsmRpcOps
import net.corda.membership.httprpc.v1.KeysRpcOps
import net.corda.membership.httprpc.v1.MGMRpcOps
import net.corda.membership.httprpc.v1.MemberLookupRpcOps
import net.corda.membership.httprpc.v1.MemberRegistrationRpcOps
import net.corda.membership.httprpc.v1.NetworkRpcOps
import net.corda.membership.httprpc.v1.types.request.HostedIdentitySetupRequest
import net.corda.membership.httprpc.v1.types.request.MemberRegistrationRequest
import net.corda.membership.httprpc.v1.types.response.RpcMemberInfo
import net.corda.messaging.api.records.Record
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.app.AuthenticatedMessageHeader
import net.corda.p2p.app.UnauthenticatedMessage
import net.corda.p2p.app.UnauthenticatedMessageHeader
import net.corda.schema.Schemas
import net.corda.test.util.eventually
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.seconds
import net.corda.v5.cipher.suite.schemes.RSA_TEMPLATE
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry

const val RPC_PORT = 443
const val P2P_PORT = 8080

const val P2P_TENANT_ID = "p2p"
const val HSM_CAT_SESSION = "SESSION_INIT"
const val HSM_CAT_LEDGER = "LEDGER"
const val HSM_CAT_TLS = "TLS"

const val P2P_GATEWAY = "corda-p2p-gateway-worker"
const val RPC_WORKER = "corda-rpc-worker"

const val MGM_CLUSTER_NS = "ccrean-cluster-mgm"
const val ALICE_CLUSTER_NS = "ccrean-cluster-a"
const val BOB_CLUSTER_NS = "ccrean-cluster-b"
const val SINGLE_CLUSTER_NS = ALICE_CLUSTER_NS

const val KEY_SCHEME = "CORDA.ECDSA.SECP256R1"
const val SIGNATURE_SPEC = "SHA256withECDSA"

const val GATEWAY_CONFIG = "corda.p2p.gateway"

val tmpPath = "build${File.separator}tmp"
private val caPath = "$tmpPath${File.separator}e2eTestCa"

data class ClusterTestData(
    val testToolkit: TestToolkit,
    val p2pHost: String,
    val members: List<MemberTestData>
) {
    val p2pUrl get() = "https://$p2pHost:${P2P_PORT}"
}

data class MemberTestData(
    private val x500Name: String
) {
    val name: String = x500Name.clearX500Name()
}

private fun String.clearX500Name(): String {
    return MemberX500Name.parse(this).toString()
}

fun getCa(): FileSystemCertificatesAuthority = CertificateAuthorityFactory
    .createFileSystemLocalAuthority(
        RSA_TEMPLATE.toFactoryDefinitions(),
        File(caPath)
    ).also { it.save() }

fun createMGMGroupPolicyJson(
    fileFormatVersion: Int = 1,
    registrationProtocol: String = "net.corda.membership.impl.registration.dynamic.mgm.MGMRegistrationService",
    syncProtocol: String = "net.corda.membership.impl.sync.dynamic.MemberSyncService"
): ByteArray {
    val groupPolicy = mapOf(
        "fileFormatVersion" to fileFormatVersion,
        "groupId" to "CREATE_ID",
        "registrationProtocol" to registrationProtocol,
        "synchronisationProtocol" to syncProtocol
    )

    return ByteArrayOutputStream().use { outputStream ->
        ObjectMapper().writeValue(outputStream, groupPolicy)
        outputStream.toByteArray()
    }
}

fun createStaticMemberGroupPolicyJson(
    ca: CertificateAuthority,
    groupId: String,
    cluster: ClusterTestData,
): ByteArray {
    val groupPolicy = mapOf(
        "fileFormatVersion" to 1,
        "groupId" to groupId,
        "registrationProtocol"
                to "net.corda.membership.impl.registration.staticnetwork.StaticMemberRegistrationService",
        "synchronisationProtocol"
                to "net.corda.membership.impl.sync.staticnetwork.StaticMemberSyncService",
        "protocolParameters" to mapOf(
            "sessionKeyPolicy" to "Combined",
            "staticNetwork" to mapOf(
                "members" to
                        cluster.members.map {
                            mapOf(
                                "name" to it.name,
                                "memberStatus" to "ACTIVE",
                                "endpointUrl-1" to cluster.p2pUrl,
                                "endpointProtocol-1" to 1
                            )
                        }
            )
        ),
        "p2pParameters" to mapOf(
            "tlsTrustRoots" to listOf(ca.caCertificate.toPem()),
            "sessionPki" to "NoPKI",
            "tlsPki" to "Standard",
            "tlsVersion" to "1.3",
            "protocolMode" to "Authentication_Encryption"
        ),
        "cipherSuite" to emptyMap<String, String>()
    )

    return ByteArrayOutputStream().use { outputStream ->
        ObjectMapper().writeValue(outputStream, groupPolicy)
        outputStream.toByteArray()
    }
}

fun toByteArray(groupPolicy: String): ByteArray {
    val json = ObjectMapper()
    return ByteArrayOutputStream().use { outputStream ->
        json.writeValue(outputStream, json.readTree(groupPolicy))
        outputStream.toByteArray()
    }
}

private val cordaVersion by lazy {
    val manifest = MemberRegistrationRpcOps::class.java.classLoader
        .getResource("META-INF/MANIFEST.MF")
        ?.openStream()
        ?.use {
            Manifest(it)
        }
    manifest?.mainAttributes?.getValue("Bundle-Version") ?: "5.0.0.0-SNAPSHOT"
}

fun TestToolkit.createEmptyJarWithManifest(groupPolicy: ByteArray): ByteArray {
    return ByteArrayOutputStream().use { outputStream ->
        val manifest = Manifest()
        manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        manifest.mainAttributes.putValue("Corda-CPB-Name", uniqueName)
        manifest.mainAttributes.putValue("Corda-CPB-Version", cordaVersion)

        JarOutputStream(outputStream, manifest).use { jarOutputStream ->
            jarOutputStream.putNextEntry(ZipEntry("GroupPolicy.json"))
            jarOutputStream.write(groupPolicy)
            jarOutputStream.closeEntry()
        }
        outputStream.toByteArray()
    }
}

fun ClusterTestData.uploadCpi(
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
                fileName = "${uniqueName}.cpb",
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

fun ClusterTestData.createVirtualNode(
    member: MemberTestData,
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
                )
            ).holdingIdentity.shortHash
        }
}

fun ClusterTestData.keyExists(
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

fun ClusterTestData.genKeyPair(
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

fun ClusterTestData.assignSoftHsm(
    holdingId: String,
    cat: String
) = with(testToolkit) {
    httpClientFor(HsmRpcOps::class.java)
        .use { client ->
            client.start().proxy.assignSoftHsm(holdingId, cat)
        }
}

fun ClusterTestData.register(
    holdingId: String,
    context: Map<String, String>
) = with(testToolkit) {
    httpClientFor(MemberRegistrationRpcOps::class.java)
        .use { client ->
            client.start().proxy.startRegistration(
                holdingId,
                MemberRegistrationRequest(
                    action = "requestJoin",
                    context = context
                )
            ).apply {
                assertThat(registrationStatus).isEqualTo("SUBMITTED")
            }
        }
}

fun ClusterTestData.generateCsr(
    member: MemberTestData,
    tlsKeyId: String
) = with(testToolkit) {
    httpClientFor(CertificatesRpcOps::class.java)
        .use { client ->
            client.start().proxy.generateCsr(
                P2P_TENANT_ID,
                tlsKeyId,
                member.name,
                HSM_CAT_TLS,
                listOf(p2pHost),
                null
            )
        }
}

fun ClusterTestData.lookupMembers(
    holdingId: String
) = with(testToolkit) {
    httpClientFor(MemberLookupRpcOps::class.java)
        .use { client ->
            client.start().proxy.lookup(holdingId).members
        }
}

fun ClusterTestData.genGroupPolicy(
    holdingId: String
) = with(testToolkit) {
    httpClientFor(MGMRpcOps::class.java).use { client ->
        client.start().proxy.generateGroupPolicy(holdingId)
    }
}

fun ClusterTestData.uploadTlsCertificate(
    certificatePem: String
) = with(testToolkit) {
    httpClientFor(CertificatesRpcOps::class.java).use { client ->
        val tlsCertAlias = "p2p-tls-cert"
        client.start().proxy.importCertificateChain(
            P2P_TENANT_ID,
            tlsCertAlias,
            listOf(
                HttpFileUpload(
                    certificatePem.byteInputStream(),
                    "$tlsCertAlias.pem"
                )
            )
        )
    }
}

fun ClusterTestData.setUpNetworkIdentity(
    holdingId: String,
    sessionKeyId: String
) = with(testToolkit) {
    httpClientFor(NetworkRpcOps::class.java).use { client ->
        val tlsCertAlias = "p2p-tls-cert"
        client.start().proxy.setupHostedIdentities(
            holdingId,
            HostedIdentitySetupRequest(
                tlsCertAlias,
                P2P_TENANT_ID,
                null,
                sessionKeyId
            )
        )
    }
}

fun ClusterTestData.disableCLRChecks() = with(testToolkit) {
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

fun ClusterTestData.assertOnlyMgmIsInMemberList(
    holdingId: String,
    mgmName: String
) = lookupMembers(holdingId).also { result ->
    assertThat(result)
        .hasSize(1)
        .allSatisfy {
            assertThat(it.mgmContext["corda.status"]).isEqualTo("ACTIVE")
            assertThat(it.memberContext["corda.name"]).isEqualTo(mgmName)
        }
}

fun getMgmRegistrationContext(
    tlsTrustRoot: String,
    sessionKeyId: String,
    ecdhKeyId: String = sessionKeyId,
    p2pUrl: String,
) = mapOf(
    "corda.session.key.id" to sessionKeyId,
    "corda.ecdh.key.id" to ecdhKeyId,
    "corda.group.protocol.registration"
            to "net.corda.membership.impl.registration.dynamic.member.DynamicMemberRegistrationService",
    "corda.group.protocol.synchronisation"
            to "net.corda.membership.impl.synchronisation.MemberSynchronisationServiceImpl",
    "corda.group.protocol.p2p.mode" to "AUTHENTICATION_ENCRYPTION",
    "corda.group.key.session.policy" to "Distinct",
    "corda.group.pki.session" to "NoPKI",
    "corda.group.pki.tls" to "Standard",
    "corda.group.tls.version" to "1.3",
    "corda.endpoints.0.connectionURL" to p2pUrl,
    "corda.endpoints.0.protocolVersion" to "1",
    "corda.group.truststore.tls.0" to tlsTrustRoot,
)

fun getMemberRegistrationContext(
    memberCluster: ClusterTestData,
    sessionKeyId: String,
    ledgerKeyId: String
) = mapOf(
    "corda.session.key.id" to sessionKeyId,
    "corda.session.key.signature.spec" to SIGNATURE_SPEC,
    "corda.ledger.keys.0.id" to ledgerKeyId,
    "corda.ledger.keys.0.signature.spec" to SIGNATURE_SPEC,
    "corda.endpoints.0.connectionURL" to memberCluster.p2pUrl,
    "corda.endpoints.0.protocolVersion" to "1"
)

/**
 * Assert that a member represented by a holding ID can find the member represented by [MemberTestData] in it's
 * member list.
 */
fun ClusterTestData.assertMemberInMemberList(
    holdingId: String,
    member: MemberTestData
) {
    eventually(
        duration = 20.seconds,
        waitBetween = 2.seconds
    ) {
        assertThat(
            lookupMembers(holdingId).map {
                it.name
            }
        ).contains(member.name)
    }
}

fun KafkaTestToolKit.assertP2pConnectivity(
    sender: HoldingIdentity,
    receiver: HoldingIdentity,
    groupId: String,
    testToolkit: TestToolkit
) {
    val traceId = "e2e-test-$groupId"
    val subSystem = "e2e-test"

    // Create authenticated messages
    val numberOfAuthenticatedMessages = 5
    val authenticatedMessagesIdToContent = (1..numberOfAuthenticatedMessages).associate {
        testToolkit.uniqueName to testToolkit.uniqueName
    }
    val authenticatedRecords = authenticatedMessagesIdToContent.map { (id, content) ->
        val messageHeader = AuthenticatedMessageHeader.newBuilder()
            .setDestination(receiver)
            .setSource(sender)
            .setTtl(null)
            .setMessageId(id)
            .setTraceId(traceId)
            .setSubsystem(subSystem)
            .build()
        val message = AuthenticatedMessage.newBuilder()
            .setHeader(messageHeader)
            .setPayload(ByteBuffer.wrap(content.toByteArray()))
            .build()
        Record(Schemas.P2P.P2P_OUT_TOPIC, testToolkit.uniqueName, AppMessage(message))
    }

    // Create unauthenticated messages
    val numberOfUnauthenticatedMessages = 3
    val unauthenticatedMessagesContent = (1..numberOfUnauthenticatedMessages).map {
        testToolkit.uniqueName
    }
    val unauthenticatedRecords = unauthenticatedMessagesContent.map { content ->
        val messageHeader = UnauthenticatedMessageHeader.newBuilder()
            .setDestination(receiver)
            .setSource(sender)
            .setSubsystem(subSystem)
            .build()
        val message = UnauthenticatedMessage.newBuilder()
            .setHeader(messageHeader)
            .setPayload(ByteBuffer.wrap(content.toByteArray()))
            .build()
        Record(Schemas.P2P.P2P_OUT_TOPIC, testToolkit.uniqueName, AppMessage(message))
    }

    // Accept messages
    val receivedAuthenticatedMessages = ConcurrentHashMap<String, String>()
    val receivedUnauthenticatedMessages = ConcurrentHashMap.newKeySet<String>()
    val countDown = CountDownLatch(numberOfUnauthenticatedMessages + numberOfAuthenticatedMessages)
    acceptRecordsFromKafka<String, AppMessage>(Schemas.P2P.P2P_IN_TOPIC) { record ->
        val message = record.value?.message
        if (message is AuthenticatedMessage) {
            if (message.header.destination.x500Name.clearX500Name() != receiver.x500Name.clearX500Name()) {
                return@acceptRecordsFromKafka
            }
            if (message.header.destination.groupId != groupId) {
                return@acceptRecordsFromKafka
            }
            if (message.header.source.x500Name.clearX500Name() != sender.x500Name.clearX500Name()) {
                return@acceptRecordsFromKafka
            }
            if (message.header.source.groupId != groupId) {
                return@acceptRecordsFromKafka
            }
            if (message.header.traceId != traceId) {
                return@acceptRecordsFromKafka
            }
            if (message.header.subsystem != subSystem) {
                return@acceptRecordsFromKafka
            }
            receivedAuthenticatedMessages[message.header.messageId] = String(message.payload.array())
            countDown.countDown()
        } else if (message is UnauthenticatedMessage) {
            if (message.header.destination.x500Name.clearX500Name() != receiver.x500Name.clearX500Name()) {
                return@acceptRecordsFromKafka
            }
            if (message.header.destination.groupId != groupId) {
                return@acceptRecordsFromKafka
            }
            if (message.header.source.x500Name.clearX500Name() != sender.x500Name.clearX500Name()) {
                return@acceptRecordsFromKafka
            }
            if (message.header.source.groupId != groupId) {
                return@acceptRecordsFromKafka
            }
            if (message.header.subsystem != subSystem) {
                return@acceptRecordsFromKafka
            }
            receivedUnauthenticatedMessages.add(String(message.payload.array()))
            countDown.countDown()
        }
    }.use {
        // Send messages
        publishRecordsToKafka(unauthenticatedRecords + authenticatedRecords)
        countDown.await(5, TimeUnit.MINUTES)
    }

    assertThat(receivedAuthenticatedMessages).containsAllEntriesOf(authenticatedMessagesIdToContent)
    assertThat(receivedUnauthenticatedMessages).containsAll(unauthenticatedMessagesContent)
}

fun FileSystemCertificatesAuthority.generateCert(csrPem: String): String {
    val request = csrPem.reader().use { reader ->
        PEMParser(reader).use { parser ->
            parser.readObject()
        }
    }?.also {
        assertThat(it).isInstanceOf(PKCS10CertificationRequest::class.java)
    }
    return signCsr(request as PKCS10CertificationRequest).toPem()
}

val RpcMemberInfo.status get() = mgmContext["corda.status"] ?: fail("Could not find member status")
val RpcMemberInfo.groupId get() = memberContext["corda.groupId"] ?: fail("Could not find member group ID")
val RpcMemberInfo.name get() = memberContext["corda.name"] ?: fail("Could not find member name")