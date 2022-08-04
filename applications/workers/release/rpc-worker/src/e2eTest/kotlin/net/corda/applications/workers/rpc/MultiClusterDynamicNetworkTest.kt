package net.corda.applications.workers.rpc

import net.corda.applications.workers.rpc.http.TestToolkitProperty
import net.corda.applications.workers.rpc.utils.ALICE_CLUSTER_NS
import net.corda.applications.workers.rpc.utils.BOB_CLUSTER_NS
import net.corda.applications.workers.rpc.utils.MGM_CLUSTER_NS
import net.corda.applications.workers.rpc.utils.MemberTestData
import net.corda.applications.workers.rpc.utils.P2P_GATEWAY
import net.corda.applications.workers.rpc.utils.RPC_PORT
import net.corda.applications.workers.rpc.utils.RPC_WORKER
import net.corda.applications.workers.rpc.utils.assertOnlyMgmIsInMemberList
import net.corda.applications.workers.rpc.utils.assignSoftHsm
import net.corda.applications.workers.rpc.utils.clearX500Name
import net.corda.applications.workers.rpc.utils.createMGMGroupPolicyJson
import net.corda.applications.workers.rpc.utils.createVirtualNode
import net.corda.applications.workers.rpc.utils.disableCLRChecks
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
import net.corda.v5.base.util.minutes
import net.corda.v5.cipher.suite.schemes.RSA_TEMPLATE
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File
import java.io.File.separator

//@Disabled("No multi cluster environment is available to run this test against. Remove this to run locally.")
class MultiClusterDynamicNetworkTest {

    private val mgmRpcHost = "$RPC_WORKER.$MGM_CLUSTER_NS"
    private val mgmP2pHost = "$P2P_GATEWAY.$MGM_CLUSTER_NS"
    private val mgmTestToolkit by TestToolkitProperty(mgmRpcHost, RPC_PORT)

    private val aliceRpcHost = "$RPC_WORKER.$ALICE_CLUSTER_NS"
    private val aliceP2pHost = "$P2P_GATEWAY.$ALICE_CLUSTER_NS"
    private val aliceTestToolkit by TestToolkitProperty(aliceRpcHost, RPC_PORT)

    private val bobRpcHost = "$RPC_WORKER.$BOB_CLUSTER_NS"
    private val bobP2pHost = "$P2P_GATEWAY.$BOB_CLUSTER_NS"
    private val bobTestToolkit by TestToolkitProperty(bobRpcHost, RPC_PORT)

    private val mgm = MemberTestData(
        "O=Mgm, L=London, C=GB, OU=${mgmTestToolkit.uniqueName}".clearX500Name(),
        mgmTestToolkit,
        mgmP2pHost
    )
    private val members = listOf(
        MemberTestData(
            "O=Alice, L=London, C=GB, OU=${aliceTestToolkit.uniqueName}".clearX500Name(),
            aliceTestToolkit,
            aliceP2pHost
        ),
        MemberTestData(
            "O=Bob, L=London, C=GB, OU=${bobTestToolkit.uniqueName}".clearX500Name(),
            bobTestToolkit,
            bobP2pHost
        )
    )

    private val ca: FileSystemCertificatesAuthority = CertificateAuthorityFactory
        .createFileSystemLocalAuthority(
            RSA_TEMPLATE.toFactoryDefinitions(),
            File("build${separator}tmp${separator}e2eTestCa")
        ).also { it.save() }

    @Test
    fun `Create mgm and allow members to join the group`() {
        val holdingIds = mutableMapOf<String, String>()
        mgm.disableCLRChecks()
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
        println(memberGroupPolicy)

        members.forEach { member ->
            member.disableCLRChecks()
            val memberCpiChecksum = member.uploadCpi(toByteArray(memberGroupPolicy))
            val memberHoldingId = member.createVirtualNode(member.name, memberCpiChecksum)
            holdingIds[member.name] = memberHoldingId
            println("${member.name} holding ID: $memberHoldingId")

            member.assignSoftHsm(memberHoldingId, hsmCategorySession)
            member.assignSoftHsm(memberHoldingId, hsmCategoryLedger)

            if (!member.keyExists(p2pTenantId, hsmCategoryTls)) {
                val memberTlsKeyId = member.genKeyPair(p2pTenantId, hsmCategoryTls)
                val memberTlsCsr = member.generateCsr(member, memberTlsKeyId)
                val memberTlsCert = ca.generateCert(memberTlsCsr)
                member.uploadTlsCertificate(memberTlsCert)
            }

            val memberSessionKeyId = member.genKeyPair(memberHoldingId, hsmCategorySession)
            val memberLedgerKeyId = member.genKeyPair(memberHoldingId, hsmCategoryLedger)

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
            println("QQQ looking at ${it.name}")
            val holdingId = holdingIds[it.name]
            assertNotNull(holdingId)
            eventually(duration = 1.minutes) {
                println("QQQ Looking out at ${it.name}")
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
