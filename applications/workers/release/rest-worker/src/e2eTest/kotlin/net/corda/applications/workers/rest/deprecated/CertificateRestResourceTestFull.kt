@file:Suppress("DEPRECATION")
package net.corda.applications.workers.rest.deprecated

import net.corda.applications.workers.rest.SessionCertificateTest
import net.corda.applications.workers.rest.utils.E2eCluster
import net.corda.applications.workers.rest.utils.E2eClusterAConfig
import net.corda.applications.workers.rest.utils.E2eClusterBConfig
import net.corda.applications.workers.rest.utils.E2eClusterCConfig
import net.corda.applications.workers.rest.utils.E2eClusterFactory
import net.corda.applications.workers.rest.utils.E2eClusterMember
import net.corda.applications.workers.rest.utils.HSM_CAT_LEDGER
import net.corda.applications.workers.rest.utils.HSM_CAT_NOTARY
import net.corda.applications.workers.rest.utils.HSM_CAT_SESSION
import net.corda.applications.workers.rest.utils.HSM_CAT_TLS
import net.corda.applications.workers.rest.utils.P2P_TENANT_ID
import net.corda.applications.workers.rest.utils.SESSION_CERT_ALIAS
import net.corda.applications.workers.rest.utils.assertAllMembersAreInMemberList
import net.corda.applications.workers.rest.utils.assertMemberInMemberList
import net.corda.applications.workers.rest.utils.assertOnlyMgmIsInMemberList
import net.corda.applications.workers.rest.utils.assignSoftHsm
import net.corda.applications.workers.rest.utils.createMemberRegistrationContext
import net.corda.applications.workers.rest.utils.createVirtualNode
import net.corda.applications.workers.rest.utils.disableLinkManagerCLRChecks
import net.corda.applications.workers.rest.utils.generateCert
import net.corda.applications.workers.rest.utils.generateCsr
import net.corda.applications.workers.rest.utils.generateGroupPolicy
import net.corda.applications.workers.rest.utils.getCa
import net.corda.applications.workers.rest.utils.getGroupId
import net.corda.applications.workers.rest.utils.getMemberName
import net.corda.applications.workers.rest.utils.isNotary
import net.corda.applications.workers.rest.utils.keyExists
import net.corda.applications.workers.rest.utils.onboardMgm
import net.corda.applications.workers.rest.utils.register
import net.corda.applications.workers.rest.utils.setSslConfiguration
import net.corda.applications.workers.rest.utils.setUpNetworkIdentity
import net.corda.applications.workers.rest.utils.uploadCpi
import net.corda.applications.workers.rest.utils.uploadSessionCertificate
import net.corda.applications.workers.rest.utils.uploadTlsCertificate
import net.corda.membership.rest.v1.CertificatesRestResource
import net.corda.membership.rest.v1.KeysRestResource
import net.corda.v5.crypto.KeySchemeCodes
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@Tag("MultiCluster")
class CertificateRestResourceTestFull {
    @TempDir
    lateinit var tempDir: Path

    private val clusterA = E2eClusterFactory.getE2eCluster(E2eClusterAConfig).apply {
        addMember(createTestMember("Alice"))
    }

    private val clusterB = E2eClusterFactory.getE2eCluster(E2eClusterBConfig).apply {
        addMember(createTestMember("Bob"))
    }

    private val clusterC = E2eClusterFactory.getE2eCluster(E2eClusterCConfig).apply {
        addMember(createTestMember("Mgm"))
    }

    private val memberClusters = listOf(clusterA, clusterB)

    @BeforeEach
    fun validSetup() {
        // Verify that test clusters are actually configured with different endpoints.
        // If not, this test isn't testing what it should.
        Assertions.assertThat(clusterA.clusterConfig.p2pHost)
            .isNotEqualTo(clusterB.clusterConfig.p2pHost)
            .isNotEqualTo(clusterC.clusterConfig.p2pHost)
        Assertions.assertThat(clusterB.clusterConfig.p2pHost)
            .isNotEqualTo(clusterC.clusterConfig.p2pHost)

        // For the purposes of this test, the MGM cluster is
        // expected to have only one MGM (in reality there can be more on a cluster).
        Assertions.assertThat(clusterC.members).hasSize(1)
    }

    @Test
    fun `Create mgm and allow members to join the group`() {
        onboardMultiClusterGroup()
    }

    /**
     * Onboard group and return group ID.
     */
    private fun onboardMultiClusterGroup(): String {
        val mgm = clusterC.members[0]

        clusterC.setSslConfiguration(false)
        clusterC.disableLinkManagerCLRChecks()
        clusterC.onboardMgm(mgm, tempDir, useSessionCertificate = true)

        val memberGroupPolicy = clusterC.generateGroupPolicy(mgm.holdingId)

        memberClusters.forEach { cordaCluster ->
            cordaCluster.setSslConfiguration(false)
            cordaCluster.disableLinkManagerCLRChecks()
            cordaCluster.deprecatedOnboardMembers(mgm, memberGroupPolicy, tempDir, useSessionCertificate = true)
        }

        // Assert all members can see each other in their member lists.
        val allMembers = memberClusters.flatMap { it.members } + mgm
        (memberClusters + clusterC).forEach { cordaCluster ->
            cordaCluster.members.forEach {
                cordaCluster.assertAllMembersAreInMemberList(it, allMembers)
            }
        }
        return clusterC.getGroupId(mgm.holdingId)
    }

    private fun E2eCluster.createTestMember(
        namePrefix: String
    ): E2eClusterMember {
        return E2eClusterMember(getMemberName<SessionCertificateTest>(namePrefix))
    }

    /**
     * Onboard all members in a cluster definition using a given CPI checksum.
     * Returns a map from member X500 name to holding ID.
     */
    private fun E2eCluster.deprecatedOnboardMembers(
        mgm: E2eClusterMember,
        memberGroupPolicy: String,
        tempDir: Path,
        useSessionCertificate: Boolean = false,
        certificateUploadedCallback: (String) -> Unit = {},
    ): List<E2eClusterMember> {
        val holdingIds = mutableListOf<E2eClusterMember>()
        val memberCpiChecksum = uploadCpi(memberGroupPolicy.toByteArray(), tempDir)
        members.forEach { member ->
            createVirtualNode(member, memberCpiChecksum)

            assignSoftHsm(member.holdingId, HSM_CAT_SESSION)
            assignSoftHsm(member.holdingId, HSM_CAT_LEDGER)
            if (member.isNotary()) {
                assignSoftHsm(member.holdingId, HSM_CAT_NOTARY)
            }

            if (!keyExists(P2P_TENANT_ID, HSM_CAT_TLS)) {
                val memberTlsKeyId = deprecatedGenerateKeyPairIfNotExists(P2P_TENANT_ID, HSM_CAT_TLS)
                val memberTlsCsr = generateCsr(member, memberTlsKeyId)
                val memberTlsCert = getCa().generateCert(memberTlsCsr)
                uploadTlsCertificate(memberTlsCert)
                certificateUploadedCallback(memberTlsCert)
            }

            val memberSessionKeyId = deprecatedGenerateKeyPairIfNotExists(member.holdingId, HSM_CAT_SESSION)

            if (useSessionCertificate) {
                val memberSessionCsr =
                    deprecatedGenerateCsr(member, memberSessionKeyId, member.holdingId, addHostToSubjectAlternativeNames = false)
                val memberSessionCert = getCa().generateCert(memberSessionCsr)
                uploadSessionCertificate(memberSessionCert, member.holdingId)
            }

            val memberLedgerKeyId = deprecatedGenerateKeyPairIfNotExists(member.holdingId, HSM_CAT_LEDGER)

            val memberNotaryKeyId = if (member.isNotary()) {
                deprecatedGenerateKeyPairIfNotExists(member.holdingId, HSM_CAT_NOTARY)
            } else null

            if (useSessionCertificate) {
                setUpNetworkIdentity(member.holdingId, memberSessionKeyId, SESSION_CERT_ALIAS)
            } else {
                setUpNetworkIdentity(member.holdingId, memberSessionKeyId)
            }


            assertOnlyMgmIsInMemberList(member.holdingId, mgm.name)
            register(
                member,
                createMemberRegistrationContext(
                    member,
                    this,
                    memberSessionKeyId,
                    memberLedgerKeyId,
                    memberNotaryKeyId
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

    private fun E2eCluster.deprecatedGenerateCsr(
        member: E2eClusterMember,
        keyId: String,
        tenantId: String = P2P_TENANT_ID,
        addHostToSubjectAlternativeNames: Boolean = true
    ): String {
        val subjectAlternativeNames = if (addHostToSubjectAlternativeNames) {
            listOf(clusterConfig.p2pHost)
        } else {
            null
        }
        return clusterHttpClientFor(CertificatesRestResource::class.java)
            .use { client ->
                client.start().proxy.generateCsr(
                    tenantId = tenantId,
                    keyId = keyId,
                    x500Name = member.name,
                    subjectAlternativeNames = subjectAlternativeNames,
                    contextMap = null
                )
            }
    }

    private fun E2eCluster.deprecatedGenerateKeyPairIfNotExists(
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
                        ?: generateKeyPair( // deprecated endpoint
                            tenantId,
                            keyAlias,
                            cat,
                            KeySchemeCodes.ECDSA_SECP256R1_CODE_NAME
                        ).id
                }
            }
    }
}