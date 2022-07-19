package net.corda.applications.workers.rpc

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.applications.workers.rpc.http.TestToolkitProperty
import net.corda.httprpc.HttpFileUpload
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRPCOps
import net.corda.libs.virtualnode.endpoints.v1.VirtualNodeRPCOps
import net.corda.libs.virtualnode.endpoints.v1.types.CreateVirtualNodeParameters
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

class StaticNetworkTest {
    private val testToolkit by TestToolkitProperty()
    private val json = ObjectMapper()
    private fun createCertificate() = StaticNetworkTest::class.java.classLoader.getResource("certificate.pem")!!.readText()
    private val groupId = testToolkit.uniqueName

    private fun createGroupPolicyJson(
        memberNames: Collection<String>,
    ): ByteArray {
        val groupPolicy = mapOf(
            "fileFormatVersion" to 1,
            "groupId" to groupId,
            "registrationProtocol" to "net.corda.membership.impl.registration.staticnetwork.StaticMemberRegistrationService",
            "synchronisationProtocol" to "net.corda.membership.impl.sync.staticnetwork.StaticMemberSyncService",
            "protocolParameters" to mapOf(
                "sessionKeyPolicy" to "Combined",
                "staticNetwork" to mapOf(
                    "members" to
                        memberNames.map {
                            mapOf(
                                "name" to it,
                                "memberStatus" to "ACTIVE",
                                "endpointUrl-1" to "http://localhost:1080",
                                "endpointProtocol-1" to 1
                            )
                        }
                )
            ),
            "p2pParameters" to mapOf(
                "sessionTrustRoots" to listOf(
                    createCertificate(),
                    createCertificate()
                ),
                "tlsTrustRoots" to listOf(
                    createCertificate()
                ),
                "sessionPki" to "Standard",
                "tlsPki" to "Standard",
                "tlsVersion" to "1.3",
                "protocolMode" to "Authentication_Encryption"
            ),
            "cipherSuite" to mapOf(
                "corda.provider" to "default",
                "corda.signature.provider" to "default",
                "corda.signature.default" to "ECDSA_SECP256K1_SHA256",
                "corda.signature.FRESH_KEYS" to "ECDSA_SECP256K1_SHA256",
                "corda.digest.default" to "SHA256",
                "corda.cryptoservice.provider" to "default"
            )
        )

        return ByteArrayOutputStream().use { outputStream ->
            json.writeValue(outputStream, groupPolicy)

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

    private fun createEmptyJarWithManifest(membersNames: Collection<String>): ByteArray {
        return ByteArrayOutputStream().use { outputStream ->
            val manifest = Manifest()
            manifest.mainAttributes[MANIFEST_VERSION] = "1.0"
            manifest.mainAttributes.putValue("Corda-CPB-Name", testToolkit.uniqueName)
            manifest.mainAttributes.putValue("Corda-CPB-Version", cordaVersion)

            JarOutputStream(outputStream, manifest).use { jarOutputStream ->
                val groupPolicy = createGroupPolicyJson(membersNames)
                jarOutputStream.putNextEntry(ZipEntry("GroupPolicy.json"))
                jarOutputStream.write(groupPolicy)
                jarOutputStream.closeEntry()
            }
            outputStream.toByteArray()
        }
    }

    private fun getCheckSum(membersNames: Collection<String>): String {
        return testToolkit.httpClientFor(CpiUploadRPCOps::class.java).use { client ->
            val proxy = client.start().proxy
            val jar = createEmptyJarWithManifest(membersNames)
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
                status.checksum
            }
        }
    }

    private fun String.clearX500Name(): String {
        return MemberX500Name.parse(this).toString()
    }

    private fun createVirtualNodes(membersNames: Collection<String>): Map<String, String> {
        val checksum = getCheckSum(membersNames)

        return testToolkit.httpClientFor(VirtualNodeRPCOps::class.java).use { client ->
            val proxy = client.start().proxy
            membersNames.associateWith {
                proxy.createVirtualNode(
                    CreateVirtualNodeParameters(
                        x500Name = it,
                        cpiFileChecksum = checksum,
                        vaultDdlConnection = null,
                        vaultDmlConnection = null,
                        cryptoDdlConnection = null,
                        cryptoDmlConnection = null,
                    )
                ).holdingIdentityShortHash
            }
        }
    }

    @Test
    fun `register members`() {
        val membersNames = (1..5).map {
            "C=GB, L=London, O=Member-${testToolkit.uniqueName}"
        }
        val holdingIds = createVirtualNodes(membersNames)

        testToolkit.httpClientFor(MemberRegistrationRpcOps::class.java).use { client ->
            val proxy = client.start().proxy
            holdingIds.values.forEach { id ->
                val registrationRequestProgress = proxy.startRegistration(
                    id,
                    MemberRegistrationRequest(
                        action = "requestJoin",
                        context = mapOf(
                            "corda.key.scheme" to "CORDA.ECDSA.SECP256R1"
                        )
                    )
                )
                assertThat(registrationRequestProgress.registrationStatus).isEqualTo("SUBMITTED")
            }
        }

        testToolkit.httpClientFor(MemberLookupRpcOps::class.java).use { client ->
            val proxy = client.start().proxy
            holdingIds.values.forEach { id ->
                val members = proxy.lookup(id).members

                assertThat(members)
                    .hasSize(holdingIds.size)
                    .allSatisfy {
                        assertThat(it.mgmContext["corda.status"]).isEqualTo("ACTIVE")
                        assertThat(it.memberContext["corda.groupId"]).isEqualTo(groupId)
                    }
                val names = members.map { it.memberContext["corda.name"] }
                assertThat(names)
                    .containsExactlyInAnyOrderElementsOf(
                        membersNames.map {
                            it.clearX500Name()
                        }
                    )
            }
        }
    }
}
