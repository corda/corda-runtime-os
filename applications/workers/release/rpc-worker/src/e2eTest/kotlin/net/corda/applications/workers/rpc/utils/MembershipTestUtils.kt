package net.corda.applications.workers.rpc.utils

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.applications.workers.rpc.http.TestToolkit
import net.corda.crypto.test.certificates.generation.CertificateAuthority
import net.corda.crypto.test.certificates.generation.toPem
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
import net.corda.test.util.eventually
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.io.ByteArrayOutputStream
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry

const val RPC_PORT = 443
const val P2P_PORT = 8080

const val p2pTenantId = "p2p"
const val hsmCategorySession = "SESSION_INIT"
const val hsmCategoryLedger = "LEDGER"
const val hsmCategoryTls = "TLS"

const val P2P_GATEWAY = "corda-p2p-gateway-worker"
const val RPC_WORKER = "corda-rpc-worker"

const val MGM_CLUSTER_NS = "ccrean-cluster-mgm"
const val ALICE_CLUSTER_NS = "ccrean-cluster-a"
const val BOB_CLUSTER_NS = "ccrean-cluster-b"
const val SINGLE_CLUSTER_NS = ALICE_CLUSTER_NS

const val KEY_SCHEME = "CORDA.ECDSA.SECP256R1"
const val SIGNATURE_SPEC = "SHA256withECDSA"

const val GATEWAY_CONFIG = "corda.p2p.gateway"

data class MemberTestData(
    val name: String,
    val testToolkit: TestToolkit,
    val p2pHost: String
) {
    val p2pUrl get() = "https://$p2pHost:${P2P_PORT}"
}

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

fun String.clearX500Name(): String {
    return MemberX500Name.parse(this).toString()
}

fun MemberTestData.uploadCpi(
    groupPolicy: ByteArray,
    isMgm: Boolean = false
) = with(testToolkit) {
    httpClientFor(CpiUploadRPCOps::class.java)
        .use { client ->
            val proxy = client.start().proxy

            // Check if MGM CPI was already uploaded in previous run. Current validation only allows one MGM CPI.
            if (isMgm) {
                proxy.getAllCpis().cpis.firstOrNull {
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
            val id = proxy.cpi(upload).id
            eventually {
                val status = proxy.status(id)
                Assertions.assertThat(status.status).isEqualTo("OK")
                status.cpiFileChecksum
            }
        }
}

fun MemberTestData.createVirtualNode(
    x500Name: String,
    cpiCheckSum: String
) = with(testToolkit) {
    httpClientFor(VirtualNodeRPCOps::class.java)
        .use { client ->
            client.start().proxy.createVirtualNode(
                VirtualNodeRequest(
                    x500Name = x500Name,
                    cpiFileChecksum = cpiCheckSum,
                    vaultDdlConnection = null,
                    vaultDmlConnection = null,
                    cryptoDdlConnection = null,
                    cryptoDmlConnection = null,
                )
            ).holdingIdentity.shortHash
        }
}


fun MemberTestData.genKeyPair(
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

fun MemberTestData.assignSoftHsm(
    holdingId: String,
    cat: String
) = with(testToolkit) {
    httpClientFor(HsmRpcOps::class.java)
        .use { client ->
            client.start().proxy.assignSoftHsm(holdingId, cat)
        }
}

fun MemberTestData.register(
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
                Assertions.assertThat(registrationStatus).isEqualTo("SUBMITTED")
            }
        }
}

fun MemberTestData.generateCsr(
    member: MemberTestData,
    tlsKeyId: String
) = with(testToolkit) {
    httpClientFor(CertificatesRpcOps::class.java)
        .use { client ->
            client.start().proxy.generateCsr(
                p2pTenantId,
                tlsKeyId,
                member.name,
                hsmCategoryTls,
                listOf(member.p2pHost),
                null
            )
        }
}

fun MemberTestData.lookupMembers(
    holdingId: String
) = with(testToolkit) {
    httpClientFor(MemberLookupRpcOps::class.java)
        .use { client ->
            client.start().proxy.lookup(holdingId).members
        }
}

fun MemberTestData.genGroupPolicy(
    holdingId: String
) = with(testToolkit) {
    httpClientFor(MGMRpcOps::class.java).use { client ->
        client.start().proxy.generateGroupPolicy(holdingId)
    }
}

fun MemberTestData.uploadTlsCertificate(
    holdingId: String,
    certificatePem: String
) = with(testToolkit) {
    httpClientFor(CertificatesRpcOps::class.java).use { client ->
        val tlsCertAlias = getTlsCertAlias(holdingId)
        client.start().proxy.importCertificateChain(
            p2pTenantId,
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

fun MemberTestData.setUpNetworkIdentity(
    holdingId: String,
    sessionKeyId: String
) = with(testToolkit) {
    httpClientFor(NetworkRpcOps::class.java).use { client ->
        val tlsCertAlias = getTlsCertAlias(holdingId)
        client.start().proxy.setupHostedIdentities(
            holdingId,
            HostedIdentitySetupRequest(
                tlsCertAlias,
                p2pTenantId,
                sessionKeyId
            )
        )
    }
}

fun MemberTestData.disableCLRChecks() = with(testToolkit) {
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

fun MemberTestData.assertOnlyMgmIsInMemberList(
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

fun getTlsCertAlias(holdingId: String) = "$holdingId-tls-cert"


fun getMgmRegistrationContext(
    tlsTrustRoot: String,
    sessionKeyId: String,
    ecdhKeyId: String = sessionKeyId,
    registrationProtocol: String = "net.corda.membership.impl.registration.dynamic.member.DynamicMemberRegistrationService",
    syncProtocol: String = "net.corda.membership.impl.synchronisation.MemberSynchronisationServiceImpl",
    p2pMode: String = "AUTHENTICATION_ENCRYPTION",
    sessionKeyPolicy: String = "Distinct",
    sessionPkiMode: String = "NoPKI",
    tlsPkiMode: String = "Standard",
    tlsVersion: String = "1.3",
    p2pUrl: String = "localhost:8080",
    p2pProtocolVersion: String = "1",
) = mapOf(
    "corda.session.key.id" to sessionKeyId,
    "corda.ecdh.key.id" to ecdhKeyId,
    "corda.group.protocol.registration" to registrationProtocol,
    "corda.group.protocol.synchronisation" to syncProtocol,
    "corda.group.protocol.p2p.mode" to p2pMode,
    "corda.group.key.session.policy" to sessionKeyPolicy,
    "corda.group.pki.session" to sessionPkiMode,
    "corda.group.pki.tls" to tlsPkiMode,
    "corda.group.tls.version" to tlsVersion,
    "corda.endpoints.0.connectionURL" to p2pUrl,
    "corda.endpoints.0.protocolVersion" to p2pProtocolVersion,
    "corda.group.truststore.tls.0" to tlsTrustRoot,
)

fun getMemberRegistrationContext(
    member: MemberTestData,
    sessionKeyId: String,
    ledgerKeyId: String
) = mapOf(
    "corda.session.key.id" to sessionKeyId,
    "corda.session.key.signature.spec" to SIGNATURE_SPEC,
    "corda.ledger.keys.0.id" to ledgerKeyId,
    "corda.ledger.keys.0.signature.spec" to SIGNATURE_SPEC,
    "corda.endpoints.0.connectionURL" to member.p2pUrl,
    "corda.endpoints.0.protocolVersion" to "1"
)

fun CertificateAuthority.generateCert(csrPem: String): String {
    val request = csrPem.reader().use { reader ->
        PEMParser(reader).use { parser ->
            parser.readObject()
        }
    }?.also {
        Assertions.assertThat(it).isInstanceOf(PKCS10CertificationRequest::class.java)
    }
    return signCsr(request as PKCS10CertificationRequest).toPem()
}