package net.corda.applications.workers.rpc

import net.corda.applications.workers.rpc.utils.E2eCluster
import net.corda.applications.workers.rpc.utils.E2eClusterAConfig
import net.corda.applications.workers.rpc.utils.E2eClusterBConfig
import net.corda.applications.workers.rpc.utils.E2eClusterCConfig
import net.corda.applications.workers.rpc.utils.E2eClusterFactory
import net.corda.applications.workers.rpc.utils.E2eClusterMember
import net.corda.applications.workers.rpc.utils.assertAllMembersAreInMemberList
import net.corda.applications.workers.rpc.utils.disableCLRChecks
import net.corda.applications.workers.rpc.utils.exec
import net.corda.applications.workers.rpc.utils.generateGroupPolicy
import net.corda.applications.workers.rpc.utils.onboardMembers
import net.corda.applications.workers.rpc.utils.onboardMgm
import net.corda.httprpc.HttpFileUpload
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRPCOps
import net.corda.libs.virtualnode.endpoints.v1.VirtualNodeRPCOps
import net.corda.membership.httprpc.v1.MemberLookupRpcOps
import net.corda.test.util.eventually
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.x500.X500Name
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Three clusters are required for running this test. See `resources/RunNetworkTests.md` for more details.
 */
class CordaConMultiClusterDynamicNetworkTest {
    private val clusterA = E2eClusterFactory.getE2eCluster(E2eClusterAConfig).also { cluster ->
        cluster.addMembers(
            listOf(
                E2eClusterMember("O=Alan, L=London, C=GB"),
                E2eClusterMember("O=Alexander, L=London, C=GB"),
                E2eClusterMember("O=Alice, L=London, C=GB"),
                E2eClusterMember("O=Andrea, L=London, C=GB"),
                E2eClusterMember("O=Bart, L=London, C=GB"),
                E2eClusterMember("O=Blake, L=London, C=GB"),
                E2eClusterMember("O=Carter, L=London, C=GB"),
                E2eClusterMember("O=Claire, L=London, C=GB"),
                E2eClusterMember("O=Diane, L=London, C=GB"),
                E2eClusterMember("O=Elly, L=London, C=GB"),
            )
        )
    }

    private val clusterB = E2eClusterFactory.getE2eCluster(E2eClusterBConfig).also { cluster ->
        cluster.addMembers(
            listOf(
                E2eClusterMember("O=Erin, L=London, C=GB"),
                E2eClusterMember("O=Evelynn, L=London, C=GB"),
                E2eClusterMember("O=Francesca, L=London, C=GB"),
                E2eClusterMember("O=George, L=London, C=GB"),
                E2eClusterMember("O=Hank, L=London, C=GB"),
                E2eClusterMember("O=Harvey, L=London, C=GB"),
                E2eClusterMember("O=Liam, L=London, C=GB"),
                E2eClusterMember("O=Lillian, L=London, C=GB"),
                E2eClusterMember("O=Luke, L=London, C=GB"),
                E2eClusterMember("O=Manuel, L=London, C=GB"),
            )
        )
    }

    private val clusterC = E2eClusterFactory.getE2eCluster(E2eClusterCConfig).also { cluster ->
        cluster.addMembers(
            listOf(
                E2eClusterMember("O=Marigold, L=London, C=GB"),
                E2eClusterMember("O=Melinda, L=London, C=GB"),
                E2eClusterMember("O=Piper, L=London, C=GB"),
                E2eClusterMember("O=Rita, L=London, C=GB"),
                E2eClusterMember("O=Sebastian, L=London, C=GB"),
                E2eClusterMember("O=MGM, L=London, C=GB", isMgm = true),
            )
        )
    }

    private val mgmCluster = clusterC

    private val memberClusters = listOf(clusterC, clusterB, clusterA)

    @Test
    fun `Create mgm print group policy file`() {
        val cpiHash = mgmCluster.testToolkit.httpClientFor(CpiUploadRPCOps::class.java).use { client ->
            val proxy = client.start().proxy
            proxy.getAllCpis().cpis.firstOrNull {
                it.id.cpiName == "mgm"
            }?.cpiFileChecksum
        } ?: uploadCpbToCluster(
                """
{
  "fileFormatVersion" : 1,
  "groupId" : "CREATE_ID",
  "registrationProtocol" :"net.corda.membership.impl.registration.dynamic.mgm.MGMRegistrationService",
  "synchronisationProtocol": "net.corda.membership.impl.synchronisation.MgmSynchronisationServiceImpl"
}
                    
                """.trimIndent(),
                File("${System.getProperty("user.home")}/corda-runtime-os/testing/cpbs/mgm/build/libs/mgm-5.0.0.0-SNAPSHOT-package.cpb"),
            "mgm",
                mgmCluster
            )

        assertThat(cpiHash).isNotBlank
        onboardMgmVnode(cpiHash)
    }

    fun createCpi(
        name: String,
        groupPolicy: String,
        cpb: File,
    ) : File {
        val temp = File.createTempFile("work", "cpi").also {
            it.deleteRecursively()
            it.deleteOnExit()
            it.mkdirs()
        }
        val groupPolicyFile = File(temp, "GroupPolicy.json").also {
            it.deleteOnExit()
        }
        groupPolicyFile.writeText(groupPolicy)
        val cpi = cpb.copyTo(File(temp, "$name.cpi")).also {
            it.deleteOnExit()
        }
        println("CPI for $name is $cpi")
        exec(
            temp,
            "zip",
            "$name.cpi",
            "-j",
            "GroupPolicy.json"
        )
        return cpi
    }

    private fun uploadCpbToCluster(
        groupPolicy: String,
        cpb: File,
        name: String,
        cluster: E2eCluster
    ): String {
        val cpi = createCpi(
            name, groupPolicy, cpb
        )
        return uploadCpi(cpi, cluster)
    }

    fun uploadCpi(
        cpi: File,
        cluster: E2eCluster
    ): String {
        return cluster.testToolkit.httpClientFor(CpiUploadRPCOps::class.java).use { client ->
            val proxy = client.start().proxy
            val id = proxy.cpi(HttpFileUpload(cpi.inputStream(), cpi.name)).id
            eventually {
                assertThat(proxy.status(id).cpiFileChecksum).isNotNull.isNotBlank()
            }
            proxy.status(id).cpiFileChecksum
        }

    }

    @Test
    fun uploadChatCpb() {
        val mgmHoldingId = mgmCluster.testToolkit.httpClientFor(VirtualNodeRPCOps::class.java).use { client ->
            client.start().proxy.getAllVirtualNodes().virtualNodes.first {
                MemberX500Name.parse(it.holdingIdentity.x500Name) == MemberX500Name.parse("O=MGM, L=London, C=GB")
            }.holdingIdentity.shortHash
        }
        /*val mgmCpiHash = mgmCluster.testToolkit.httpClientFor(CpiUploadRPCOps::class.java).use { client ->
            val proxy = client.start().proxy
            proxy.getAllCpis().cpis.first {
                it.id.cpiName == "mgm"
            }.cpiFileChecksum
        }
        println("QQQ $mgmCpiHash")*/

        val cpb = File("${System.getProperty("user.home")}/corda-runtime-os/testing/cpbs/chat/build/libs/chat-5.0.0.0-SNAPSHOT-package.cpb")
        val gp = mgmCluster.generateGroupPolicy(mgmHoldingId)
        val cpi = createCpi(
            "chat",
            gp,
            cpb
        )
        memberClusters.forEach { cluster ->
            val cpiFileChecksum =uploadCpi(cpi, cluster)
            println("For ${cluster.p2pUrl} - $cpiFileChecksum")
        }
    }

    @Test
    fun all() {
        println("Creating mgm...")
        `Create mgm print group policy file`()
        println("Uploading chat...")
        uploadChatCpb()
        println("Onboard members...")
        `onboard members`()
    }

    fun kubectl(vararg args: String): String {
        return exec(File(System.getProperty("user.home")),
            "kubectl",
            *args)
    }

    @Test
    fun rebuildClusters() {
        val baseImage = "preTest-528cec354"
        memberClusters.forEach {
            kubectl("delete", "ns", it.clusterConfig.clusterName)
        }
        memberClusters.forEach {
            kubectl("create", "ns", it.clusterConfig.clusterName)
            kubectl("label","ns", it.clusterConfig.clusterName, "namespace-type=corda-e2e", "--overwrite=true")
            kubectl("label","ns", it.clusterConfig.clusterName, "branch=yift/core-6502/demo-issue-from-charlie", "--overwrite=true")

            exec(File("${System.getProperty("user.home")}/corda-dev-helm"),
                "helm",
                "repo",
                "add", "bitnami", "https://charts.bitnami.com/bitnami"
            )
            exec(File("${System.getProperty("user.home")}/corda-dev-helm"),
                "helm",
                "dependency", "build", "charts/corda-dev",
            )
            exec(File("${System.getProperty("user.home")}/corda-dev-helm"),
                "helm",
                "upgrade", "--install", "prereqs", "-n","corda",
                "charts/corda-dev",
                "--set", "kafka.replicaCount=1,kafka.zookeeper.replicaCount=1", "-n",
                it.clusterConfig.clusterName,
                "--wait",
                "--timeout", "600s"
            )

            exec(File("${System.getProperty("user.home")}/corda-runtime-os"),
                "helm",
                "install" ,"corda",
                "./charts/corda", "-f", ".ci/e2eTests/corda.yaml",
                "--set",
                "image.tag=$baseImage,bootstrap.kafka.replicas=1,kafka.sasl.enabled=false",
                "-n",
                it.clusterConfig.clusterName,
                "--wait",
                "--timeout", "600s"
            )
        }
    }

    // EC7D44F7E1F7

    @Test
    //@Disabled("Enable this to onboard Members")
    fun `onboard members`() {
        onboardMembers()
    }

    private fun onboardMgmVnode(cpiHash: String) {
        // For the purposes of this test, the MGM cluster is
        // expected to have only one MGM (in reality there can be more on a cluster).
        //assertThat(mgmCluster.members).hasSize(1)

        val mgm = mgmCluster.members.first { it.isMgm }

        mgmCluster.disableCLRChecks()
        mgmCluster.onboardMgm(mgm, cpiHash)

        val memberGroupPolicy = mgmCluster.generateGroupPolicy(mgm.holdingId)
        println(memberGroupPolicy)
    }

    private fun onboardMembers() {
        val mgm = mgmCluster.members.first { it.isMgm }

        memberClusters.forEach { cordaCluster ->
            cordaCluster.disableCLRChecks()
            val cpiHash = cordaCluster.testToolkit.httpClientFor(CpiUploadRPCOps::class.java).use { client ->
                val proxy = client.start().proxy
                proxy.getAllCpis().cpis.first {
                    it.id.cpiName == "chat"
                }.cpiFileChecksum
            }
            // SET CPI HASH FROM WHAT WAS MANUALLY UPLOADED - uploading same CPI to three different
            // clusters seems to result in same hash for all three
            // val cpiHash = ""

            assertThat(cpiHash).isNotBlank

            cordaCluster.onboardMembers(mgm, cpiHash = cpiHash)
        }

        // Assert all members can see each other in their member lists.
        val allMembers = memberClusters.flatMap { it.members } + mgm
        (memberClusters).forEach { cordaCluster ->
            cordaCluster.members.forEach {
                cordaCluster.assertAllMembersAreInMemberList(it, allMembers)
            }
        }
    }

    @Test
    fun verifyAllMembers() {
        val allMembers = memberClusters.flatMap { it.members }
        (memberClusters).forEach { cordaCluster ->
            println("Looking at ${cordaCluster.clusterConfig.clusterName}")
            cordaCluster.testToolkit.httpClientFor(VirtualNodeRPCOps::class.java).use {
                it.start().proxy.getAllVirtualNodes().virtualNodes.forEach {
                    val name = it.holdingIdentity.x500Name
                    val hash = it.holdingIdentity.shortHash
                    cordaCluster.members.forEach {
                        if(name == it.name) {
                            println("Setting hash $hash to $name")
                            it.holdingId = hash
                        }
                    }
                }
            }
            cordaCluster.members.forEach {
                println("\t Looking at ${it.name}")
                cordaCluster.assertAllMembersAreInMemberList(it, allMembers)
            }
        }

    }
}
