package net.corda.applications.workers.rpc

import net.corda.applications.workers.rpc.http.TestToolkitProperty
import net.corda.applications.workers.rpc.utils.MemberTestData
import net.corda.applications.workers.rpc.utils.P2P_GATEWAY
import net.corda.applications.workers.rpc.utils.RPC_PORT
import net.corda.applications.workers.rpc.utils.RPC_WORKER
import net.corda.applications.workers.rpc.utils.SINGLE_CLUSTER_NS
import net.corda.applications.workers.rpc.utils.assertOnlyMgmIsInMemberList
import net.corda.applications.workers.rpc.utils.assignSoftHsm
import net.corda.applications.workers.rpc.utils.clearX500Name
import net.corda.applications.workers.rpc.utils.createMGMGroupPolicyJson
import net.corda.applications.workers.rpc.utils.createVirtualNode
import net.corda.applications.workers.rpc.utils.genGroupPolicy
import net.corda.applications.workers.rpc.utils.genKeyPair
import net.corda.applications.workers.rpc.utils.generateCert
import net.corda.applications.workers.rpc.utils.generateCsr
import net.corda.applications.workers.rpc.utils.getMemberRegistrationContext
import net.corda.applications.workers.rpc.utils.getMgmRegistrationContext
import net.corda.applications.workers.rpc.utils.hsmCategoryLedger
import net.corda.applications.workers.rpc.utils.hsmCategorySession
import net.corda.applications.workers.rpc.utils.hsmCategoryTls
import net.corda.applications.workers.rpc.utils.keyExists
import net.corda.applications.workers.rpc.utils.lookupMembers
import net.corda.applications.workers.rpc.utils.p2pTenantId
import net.corda.applications.workers.rpc.utils.register
import net.corda.applications.workers.rpc.utils.setUpNetworkIdentity
import net.corda.applications.workers.rpc.utils.toByteArray
import net.corda.applications.workers.rpc.utils.uploadCpi
import net.corda.applications.workers.rpc.utils.uploadTlsCertificate
import net.corda.crypto.test.certificates.generation.CertificateAuthorityFactory
import net.corda.crypto.test.certificates.generation.FileSystemCertificatesAuthority
import net.corda.crypto.test.certificates.generation.toFactoryDefinitions
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.test.util.eventually
import net.corda.v5.cipher.suite.schemes.RSA_TEMPLATE
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.io.File

class SingleClusterDynamicNetworkTest {
    companion object {
        // If running deployment in eks, ensure this is set to true to use correct endpoint information.
        // This should be false by default for automated builds.
        private const val IS_REMOTE_CLUSTER = false
    }

    private val remoteRpcHost = "$RPC_WORKER.$SINGLE_CLUSTER_NS"
    private val remoteP2pHost = "$P2P_GATEWAY.$SINGLE_CLUSTER_NS"
    private val remoteTestToolkit by TestToolkitProperty(remoteRpcHost, RPC_PORT)
    private val localTestToolkit by TestToolkitProperty()

    private val p2pHost = if (IS_REMOTE_CLUSTER) remoteP2pHost else "https://localhost:8080"
    private val testToolkit = if (IS_REMOTE_CLUSTER) remoteTestToolkit else localTestToolkit

    private val mgm = MemberTestData(
        "O=Mgm, L=London, C=GB, OU=${testToolkit.uniqueName}".clearX500Name(),
        testToolkit,
        p2pHost
    )
    private val members = listOf(
        MemberTestData(
            "O=Alice, L=London, C=GB, OU=${testToolkit.uniqueName}".clearX500Name(),
            testToolkit,
            p2pHost
        ),
        MemberTestData(
            "O=Bob, L=London, C=GB, OU=${testToolkit.uniqueName}".clearX500Name(),
            testToolkit,
            p2pHost
        )
    )

    private val ca: FileSystemCertificatesAuthority = CertificateAuthorityFactory
        .createFileSystemLocalAuthority(
            RSA_TEMPLATE.toFactoryDefinitions(),
            File("build${File.separator}tmp${File.separator}e2eTestCa")
        ).also { it.save() }

    @Test
    fun `Create mgm and allow members to join the group`() {
        val holdingIds = mutableMapOf<String, String>()
        val cpiChecksum = mgm.uploadCpi(createMGMGroupPolicyJson(), true)
        val mgmHoldingId = mgm.createVirtualNode(mgm.name, cpiChecksum)
        holdingIds[mgm.name] = mgmHoldingId
        println("MGM HoldingIdentity: $mgmHoldingId")

        mgm.assignSoftHsm(mgmHoldingId, hsmCategorySession)

        val mgmSessionKeyId = mgm.genKeyPair(mgmHoldingId, hsmCategorySession)

        mgm.register(
            mgmHoldingId,
            getMgmRegistrationContext(
                tlsTrustRoot = ca.caCertificate.toPem(),
                sessionKeyId = mgmSessionKeyId,
                p2pUrl = mgm.p2pUrl
            )
        )
        mgm.assertOnlyMgmIsInMemberList(mgmHoldingId, mgm.name)

        if (!mgm.keyExists(p2pTenantId, hsmCategoryTls)) {
            val mgmTlsKeyId = mgm.genKeyPair(p2pTenantId, hsmCategoryTls)
            val mgmTlsCsr = mgm.generateCsr(mgm, mgmTlsKeyId)
            val mgmTlsCert = ca.generateCert(mgmTlsCsr)
            mgm.uploadTlsCertificate(mgmTlsCert)
        }

        mgm.setUpNetworkIdentity(
            mgmHoldingId,
            mgmSessionKeyId
        )

        val memberGroupPolicy = mgm.genGroupPolicy(mgmHoldingId)

        val memberCpiChecksum = members[0].uploadCpi(toByteArray(memberGroupPolicy))
        members.forEach { member ->
            val memberHoldingId = member.createVirtualNode(member.name, memberCpiChecksum)
            holdingIds[member.name] = memberHoldingId
            println("${member.name} holding ID: $memberHoldingId")

            member.assignSoftHsm(memberHoldingId, hsmCategorySession)
            member.assignSoftHsm(memberHoldingId, hsmCategoryLedger)

            val memberSessionKeyId = member.genKeyPair(memberHoldingId, hsmCategorySession)
            val memberLedgerKeyId = member.genKeyPair(memberHoldingId, hsmCategoryLedger)

            if (!member.keyExists(p2pTenantId, hsmCategoryTls)) {
                val memberTlsKeyId = member.genKeyPair(p2pTenantId, hsmCategoryTls)
                val memberTlsCsr = member.generateCsr(member, memberTlsKeyId)
                val memberTlsCert = ca.generateCert(memberTlsCsr)
                member.uploadTlsCertificate(memberTlsCert)
            }

            member.setUpNetworkIdentity(
                memberHoldingId,
                memberSessionKeyId
            )
            member.assertOnlyMgmIsInMemberList(memberHoldingId, mgm.name)

            member.register(
                memberHoldingId,
                getMemberRegistrationContext(
                    member,
                    memberSessionKeyId,
                    memberLedgerKeyId
                )
            )
        }

        (members + mgm).forEach {
            val holdingId = holdingIds[it.name]
            assertNotNull(holdingId)
            eventually {
                it.lookupMembers(holdingId!!).also { result ->
                    assertThat(result)
                        .hasSize(1 + members.size)
                        .allSatisfy { memberInfo ->
                            assertThat(memberInfo.mgmContext["corda.status"]).isEqualTo("ACTIVE")
                        }
                    val expectedList = members.map { member -> member.name } + mgm.name
                    assertThat(result.map { memberInfo -> memberInfo.memberContext["corda.name"] })
                        .hasSize(1 + members.size)
                        .containsExactlyInAnyOrderElementsOf(expectedList)
                }
            }
        }
    }
}
