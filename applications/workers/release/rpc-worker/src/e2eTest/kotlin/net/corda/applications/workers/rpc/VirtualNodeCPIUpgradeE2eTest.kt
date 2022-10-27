package net.corda.applications.workers.rpc

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.UUID
import kotlin.io.path.createTempDirectory
import net.corda.applications.workers.rpc.http.SkipWhenRpcEndpointUnavailable
import net.corda.applications.workers.rpc.utils.E2eCluster
import net.corda.applications.workers.rpc.utils.E2eClusterFactory
import net.corda.applications.workers.rpc.utils.E2eClusterMember
import net.corda.applications.workers.rpc.utils.HSM_CAT_LEDGER
import net.corda.applications.workers.rpc.utils.HSM_CAT_SESSION
import net.corda.applications.workers.rpc.utils.HSM_CAT_TLS
import net.corda.applications.workers.rpc.utils.KEY_SCHEME
import net.corda.applications.workers.rpc.utils.P2P_TENANT_ID
import net.corda.applications.workers.rpc.utils.assertAllMembersAreInMemberList
import net.corda.applications.workers.rpc.utils.assertMemberInMemberList
import net.corda.applications.workers.rpc.utils.assignSoftHsm
import net.corda.applications.workers.rpc.utils.createMGMGroupPolicyJson
import net.corda.applications.workers.rpc.utils.createMemberRegistrationContext
import net.corda.applications.workers.rpc.utils.createStaticMemberGroupPolicyJson
import net.corda.applications.workers.rpc.utils.createVirtualNode
import net.corda.applications.workers.rpc.utils.generateCert
import net.corda.applications.workers.rpc.utils.generateCsr
import net.corda.applications.workers.rpc.utils.generateGroupPolicy
import net.corda.applications.workers.rpc.utils.generateKeyPairIfNotExists
import net.corda.applications.workers.rpc.utils.getCa
import net.corda.applications.workers.rpc.utils.getGroupId
import net.corda.applications.workers.rpc.utils.keyExists
import net.corda.applications.workers.rpc.utils.onboardMembers
import net.corda.applications.workers.rpc.utils.onboardMgm
import net.corda.applications.workers.rpc.utils.register
import net.corda.applications.workers.rpc.utils.setUpNetworkIdentity
import net.corda.applications.workers.rpc.utils.uploadCpi
import net.corda.applications.workers.rpc.utils.uploadTlsCertificate
import net.corda.utilities.deleteRecursively
import net.corda.utilities.readAll
import org.junit.jupiter.api.Test
import net.corda.cli.plugins.packaging.CreateCpiV2
import net.corda.cli.plugins.packaging.signing.SigningOptions
import net.corda.crypto.test.certificates.generation.CertificateAuthority
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.httprpc.HttpFileUpload
import net.corda.membership.httprpc.v1.CertificatesRpcOps

@SkipWhenRpcEndpointUnavailable
class VirtualNodeCPIUpgradeE2eTest {

    private val cordaCluster = E2eClusterFactory.getE2eCluster().also { cluster ->
        cluster.addMembers(
            listOf(
                E2eClusterMember("C=GB, L=London, O=Member-${cluster.testToolkit.uniqueName}"),
                E2eClusterMember("C=GB, L=London, O=Member-${cluster.testToolkit.uniqueName}")
            )
        )
    }

    private val mgm = E2eClusterMember(
        "O=Mgm, L=London, C=GB, OU=${cordaCluster.testToolkit.uniqueName}"
    )

    @Test
    fun `create members with a virtual node`() {

        val groupId = UUID.randomUUID().toString()
        val groupPolicy = String(
            createStaticMemberGroupPolicyJson(
                getCa(),
                groupId,
                cordaCluster
            )
        )
        val cpbResourceLocation = "/META-INF/cpi-for-version-upgrade-v1/version-sensitive-cordapp.cpb"
        val cpiVersion = "1.0.0.101-SNAPSHOT"
        val cpiName = "version-sensitive-cpi"
        with(cordaCluster) {
            importCertificate("/cordadevcodesign.pem", "codesigner", "cordadev")
            val cpiChecksum = uploadCpi(
                cpbToCpi(
                    getInputStream(cpbResourceLocation),
                    groupPolicy,
                    cpiName,
                    cpiVersion
                ),
                "$cpiName-$cpiVersion.cpi"
            )
            members.forEach { member ->
                createVirtualNode(member, cpiChecksum)
                assertMemberInMemberList(member.holdingId, member)
            }
        }
    }

    private fun E2eCluster.importCertificate(resourceLocation: String, tenant: String, alias: String) {
        val cert = getInputStream(resourceLocation)
        with(testToolkit) {
            httpClientFor(CertificatesRpcOps::class.java)
                .use { client ->
                    client.start().proxy.importCertificateChain(tenant, alias, listOf(HttpFileUpload(cert, "cordadevcodesign.pem")))
                }
        }

    }

    private fun getInputStream(resourceName: String): InputStream {
        return this::class.java.getResource(resourceName)?.openStream()
            ?: throw FileNotFoundException("No such resource: '$resourceName'")
    }

    /** Returns a new input stream
     * Don't use this method when we have actual CPIs
     */
    private fun cpbToCpi(
        inputStream: InputStream,
        groupPolicy: String,
        cpiNameValue: String,
        cpiVersionValue: String
    ): InputStream {

        val tempDirectory = createTempDirectory()
        try {
            // Save CPB to disk
            val cpbPath = tempDirectory.resolve("cpb.cpb")
            Files.newOutputStream(cpbPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW).use {
                inputStream.copyTo(it)
            }

            // Save group policy to disk
            val groupPolicyPath = tempDirectory.resolve("groupPolicy")
            Files.newBufferedWriter(groupPolicyPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW).use {
                it.write(groupPolicy)
            }

            // Save keystore to disk
            val keyStorePath = tempDirectory.resolve("cordadevcodesign.p12")
            Files.newOutputStream(keyStorePath, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW).use {
                it.write(getKeyStore())
            }

            // Create CPI
            val cpiPath = tempDirectory.resolve("cpi")
            CreateCpiV2().apply {
                cpbFileName = cpbPath.toString()
                cpiName = cpiNameValue
                cpiVersion = cpiVersionValue
                cpiUpgrade = false
                groupPolicyFileName = groupPolicyPath.toString()
                outputFileName = cpiPath.toString()
                signingOptions = SigningOptions().apply {
                    keyStoreFileName = keyStorePath.toString()
                    keyStorePass = "cordacadevpass"
                    keyAlias = "cordacodesign"
                }
            }.run()

            // Read CPI
            return cpiPath.readAll().inputStream()
        } finally {
            tempDirectory.deleteRecursively()
        }
    }

    private fun getKeyStore() = javaClass.classLoader.getResourceAsStream("cordadevcodesign.p12")?.use { it.readAllBytes() }
        ?: throw Exception("cordadevcodesign.p12 not found")
}