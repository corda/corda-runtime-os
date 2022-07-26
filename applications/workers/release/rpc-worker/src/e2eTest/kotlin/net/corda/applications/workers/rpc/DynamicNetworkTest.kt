package net.corda.applications.workers.rpc

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.applications.workers.rpc.http.TestToolkitProperty
import net.corda.httprpc.HttpFileUpload
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRPCOps
import net.corda.libs.virtualnode.endpoints.v1.VirtualNodeRPCOps
import net.corda.libs.virtualnode.endpoints.v1.types.CreateVirtualNodeParameters
import net.corda.membership.httprpc.v1.CertificatesRpcOps
import net.corda.membership.httprpc.v1.HsmRpcOps
import net.corda.membership.httprpc.v1.KeysRpcOps
import net.corda.membership.httprpc.v1.MGMRpcOps
import net.corda.membership.httprpc.v1.MemberLookupRpcOps
import net.corda.membership.httprpc.v1.MemberRegistrationRpcOps
import net.corda.membership.httprpc.v1.types.request.MemberRegistrationRequest
import net.corda.test.util.eventually
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.jar.Attributes.Name.MANIFEST_VERSION
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry

class DynamicNetworkTest {
    private val testToolkit by TestToolkitProperty()
    private val json = ObjectMapper()

    private fun getFakeRootCaCert(): String =
        DynamicNetworkTest::class.java.classLoader.getResource("fake-ca-root.pem")!!.readText()

    @Test
    fun `Create mgm and allow members to join the group`() {
        val p2pTenantId = "p2p"
        val hsmCategorySession = "SESSION_INIT"
        val hsmCategoryLedger = "LEDGER"
        val hsmCategoryTls = "TLS"
        val mgmName = "O=Mgm, L=London, C=GB, OU=${testToolkit.uniqueName}".clearX500Name()
        val cpiChecksum = uploadCpi(createMGMGroupPolicyJson())
        val mgmHoldingId = createVirtualNode(mgmName, cpiChecksum)

        // assign HSM
        testToolkit.httpClientFor(HsmRpcOps::class.java).use { client ->
            with(client.start().proxy) {
                assignSoftHsm(mgmHoldingId, hsmCategorySession)
            }
        }


        val (mgmSessionKeyId, mgmTlsKeyId) = testToolkit.httpClientFor(KeysRpcOps::class.java).use { client ->
            with(client.start().proxy) {
                val session = generateKeyPair(
                    mgmHoldingId,
                    "$mgmHoldingId-$hsmCategorySession",
                    hsmCategorySession,
                    "CORDA.ECDSA.SECP256R1"
                )
                val tls = generateKeyPair(
                    p2pTenantId,
                    "$mgmHoldingId-$hsmCategoryTls",
                    hsmCategoryTls,
                    "CORDA.ECDSA.SECP256R1"
                )
                Pair(session, tls)
            }
        }

        val mgmRegistrationContext = mapOf(
            "corda.session.key.id" to mgmSessionKeyId,
            "corda.ecdh.key.id" to mgmSessionKeyId, // temporary
            "corda.group.protocol.registration"
                    to "net.corda.membership.impl.registration.dynamic.member.DynamicMemberRegistrationService",
            "corda.group.protocol.synchronisation"
                    to "net.corda.membership.impl.synchronisation.MemberSynchronisationServiceImpl",
            "corda.group.protocol.p2p.mode" to "AUTHENTICATION_ENCRYPTION",
            "corda.group.key.session.policy" to "Distinct",
            "corda.group.pki.session" to "NoPKI",
            "corda.group.pki.tls" to "Standard",
            "corda.group.tls.version" to "1.3",
            "corda.endpoints.0.connectionURL" to "localhost:1080",
            "corda.endpoints.0.protocolVersion" to "1",
            "corda.group.truststore.tls.0" to getFakeRootCaCert(),
            "corda.group.truststore.session.0" to getFakeRootCaCert()
        )

        testToolkit.httpClientFor(MemberRegistrationRpcOps::class.java).use { client ->
            val proxy = client.start().proxy
            val registrationRequestProgress = proxy.startRegistration(
                mgmHoldingId,
                MemberRegistrationRequest(
                    action = "requestJoin",
                    context = mgmRegistrationContext
                )
            )
            assertThat(registrationRequestProgress.registrationStatus).isEqualTo("SUBMITTED")
        }

        val groupId = testToolkit.httpClientFor(MemberLookupRpcOps::class.java).use { client ->
            val members = client.start().proxy.lookup(mgmHoldingId).members

            assertThat(members)
                .hasSize(1)
                .allSatisfy {
                    assertThat(it.mgmContext["corda.status"]).isEqualTo("ACTIVE")
                    assertThat(it.memberContext["corda.name"]).isEqualTo(mgmName)
                }
            members.first().memberContext["corda.groupId"]
        }

        val mgmTlsCsr = testToolkit.httpClientFor(CertificatesRpcOps::class.java).use { client ->
            with(client.start().proxy) {
                generateCsr(p2pTenantId, mgmTlsKeyId, mgmName, hsmCategoryTls, null, null)
            }
        }

        println(
            """
            mgmName: $mgmName
            mgmHoldingId: $mgmHoldingId
            Group ID: $groupId
            
            mgm CSR: 
            $mgmTlsCsr
            
            Upload cert with:
            curl -k -u admin:admin -X PUT  -F certificate=@{path to cert} -F alias=$mgmHoldingId-tls-cert "https://localhost:8888/api/v1/certificates/$p2pTenantId"
            
            Finalise network setup with:
            curl -k -u admin:admin -X PUT -d '{"certificateChainAlias": "$mgmHoldingId-tls-cert", "tlsTenantId": "p2p", "mgmSessionKeyId": "$mgmSessionKeyId"}' "https://localhost:8888/api/v1/network/setup/$mgmHoldingId"
        """.trimIndent()
        )

        val memberGroupPolicy = testToolkit.httpClientFor(MGMRpcOps::class.java).use { client ->
            val proxy = client.start().proxy
            proxy.generateGroupPolicy(mgmHoldingId)
        }

        val memberCpiChecksum = uploadCpi(toByteArray(memberGroupPolicy))

        val memberNames = listOf(
            "O=Alice, L=London, C=GB, OU=${testToolkit.uniqueName}".clearX500Name(),
            "O=Bob, L=London, C=GB, OU=${testToolkit.uniqueName}".clearX500Name(),
            "O=Charlie, L=London, C=GB, OU=${testToolkit.uniqueName}".clearX500Name(),
            "O=Denis, L=London, C=GB, OU=${testToolkit.uniqueName}".clearX500Name(),
            "O=Elaine, L=London, C=GB, OU=${testToolkit.uniqueName}".clearX500Name(),
        )

        memberNames.forEach { memberName ->
            val memberHoldingId = createVirtualNode(memberName, memberCpiChecksum)

            testToolkit.httpClientFor(HsmRpcOps::class.java).use {
                with(it.start().proxy) {
                    assignSoftHsm(memberHoldingId, hsmCategorySession)
                    assignSoftHsm(memberHoldingId, hsmCategoryLedger)
                }
            }

            val (memberSessionKeyId, memberLedgerKeyId, memberTlsKeyId) = testToolkit.httpClientFor(KeysRpcOps::class.java).use {
                with(it.start().proxy) {
                    val memberSessionKeyId = generateKeyPair(
                        memberHoldingId,
                        "$memberHoldingId-$hsmCategorySession",
                        hsmCategorySession,
                        "CORDA.ECDSA.SECP256R1"
                    )
                    val memberLedgerKeyId = generateKeyPair(
                        memberHoldingId,
                        "$memberHoldingId-$hsmCategoryLedger",
                        hsmCategoryLedger,
                        "CORDA.ECDSA.SECP256R1"
                    )
                    val memberTlsKeyId = generateKeyPair(
                        p2pTenantId,
                        "$memberHoldingId-$hsmCategoryTls",
                        hsmCategoryTls,
                        "CORDA.ECDSA.SECP256R1"
                    )
                    Triple(memberSessionKeyId, memberLedgerKeyId, memberTlsKeyId)
                }
            }

            val memberRegistrationContext = mapOf(
                "corda.session.key.id" to memberSessionKeyId,
                "corda.ledger.keys.0.id" to memberLedgerKeyId,
                "corda.ledger.keys.0.signature.spec" to "CORDA.ECDSA.SECP256R1",
                "corda.endpoints.0.connectionURL" to "localhost:1080",
                "corda.endpoints.0.protocolVersion" to "1"
            )

//            testToolkit.httpClientFor(MemberRegistrationRpcOps::class.java).use { client ->
//                with(client.start().proxy) {
//                    val registrationRequestProgress = startRegistration(
//                        memberHoldingId,
//                        MemberRegistrationRequest(
//                            action = "requestJoin",
//                            context = memberRegistrationContext
//                        )
//                    )
//                    assertThat(registrationRequestProgress.registrationStatus).isEqualTo("SUBMITTED")
//                }
//            }

            val memberTlsCsr = testToolkit.httpClientFor(CertificatesRpcOps::class.java).use { client ->
                with(client.start().proxy) {
                    generateCsr(p2pTenantId, memberTlsKeyId, memberName, hsmCategoryTls, null, null)
                }
            }

            val stringContext = ObjectMapper().writeValueAsString(memberRegistrationContext)
            println(
                """                                    
                    memberName: $memberName
                    memberHoldingId: $memberHoldingId
                    Group ID: $groupId
                """.trimIndent()
            )
            println(
                """
                    $memberName CSR: 
                    $memberTlsCsr
                                
                    Upload cert with:
                    curl -k -u admin:admin -X PUT  -F certificate=@{path to cert} -F alias=$memberHoldingId-tls-cert "https://localhost:8888/api/v1/certificates/$p2pTenantId"
                                
                    Finalise network setup with:
                    curl -k -u admin:admin -X PUT -d '{"certificateChainAlias": "$memberHoldingId-tls-cert", "tlsTenantId": "p2p", "mgmSessionKeyId": "$memberSessionKeyId"}' "https://localhost:8888/api/v1/network/setup/$memberHoldingId"
                                
                    Register $memberName with:
                    curl --insecure -u admin:admin -d '{ "memberRegistrationRequest": { "action": "requestJoin", "context": $stringContext } }' https://localhost:8888/api/v1/membership/$memberHoldingId
                """.trimIndent()
            )
        }
    }

    private fun createMGMGroupPolicyJson(): ByteArray {
        val groupPolicy = mapOf(
            "fileFormatVersion" to 1,
            "groupId" to "CREATE_ID",
            "registrationProtocol" to "net.corda.membership.impl.registration.dynamic.mgm.MGMRegistrationService",
            "synchronisationProtocol" to "net.corda.membership.impl.sync.dynamic.MemberSyncService",
        )

        return ByteArrayOutputStream().use { outputStream ->
            json.writeValue(outputStream, groupPolicy)

            outputStream.toByteArray()
        }
    }

    private fun toByteArray(groupPolicy: String): ByteArray {
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

    private fun createEmptyJarWithManifest(groupPolicy: ByteArray): ByteArray {
        return ByteArrayOutputStream().use { outputStream ->
            val manifest = Manifest()
            manifest.mainAttributes[MANIFEST_VERSION] = "1.0"
            manifest.mainAttributes.putValue("Corda-CPB-Name", testToolkit.uniqueName)
            manifest.mainAttributes.putValue("Corda-CPB-Version", cordaVersion)

            JarOutputStream(outputStream, manifest).use { jarOutputStream ->
                jarOutputStream.putNextEntry(ZipEntry("GroupPolicy.json"))
                jarOutputStream.write(groupPolicy)
                jarOutputStream.closeEntry()
            }
            outputStream.toByteArray()
        }
    }

    private fun uploadCpi(groupPolicy: ByteArray): String {
        return testToolkit.httpClientFor(CpiUploadRPCOps::class.java).use { client ->
            val proxy = client.start().proxy
            val jar = createEmptyJarWithManifest(groupPolicy)
            val upload = HttpFileUpload(
                content = jar.inputStream(),
                contentType = "application/java-archive",
                extension = "cpb",
                fileName = "${testToolkit.uniqueName}.cpb",
                size = jar.size.toLong(),
            )
            val id = proxy.cpi(upload).id
            eventually {
                val status = proxy.status(id)
                assertThat(status.status).isEqualTo("OK")
                status.cpiFileChecksum
            }
        }
    }

    private fun String.clearX500Name(): String {
        return MemberX500Name.parse(this).toString()
    }

    private fun createVirtualNode(x500Name: String, cpiCheckSum: String): String {
        return testToolkit.httpClientFor(VirtualNodeRPCOps::class.java).use { client ->
            val proxy = client.start().proxy
            proxy.createVirtualNode(
                CreateVirtualNodeParameters(
                    x500Name = x500Name,
                    cpiFileChecksum = cpiCheckSum,
                    vaultDdlConnection = null,
                    vaultDmlConnection = null,
                    cryptoDdlConnection = null,
                    cryptoDmlConnection = null,
                )
            ).holdingIdentityShortHash
        }
    }
}
