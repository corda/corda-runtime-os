@file:Suppress("DEPRECATION")
package net.corda.applications.workers.rest.deprecated

import net.corda.applications.workers.rest.StaticNetworkTest
import net.corda.applications.workers.rest.utils.E2eCluster
import net.corda.applications.workers.rest.utils.E2eClusterFactory
import net.corda.applications.workers.rest.utils.E2eClusterMember
import net.corda.applications.workers.rest.utils.E2eClusterMemberRole
import net.corda.applications.workers.rest.utils.E2eClusterMemberRole.NOTARY
import net.corda.applications.workers.rest.utils.assertAllMembersAreInMemberList
import net.corda.applications.workers.rest.utils.createStaticMemberGroupPolicyJson
import net.corda.applications.workers.rest.utils.getCa
import net.corda.applications.workers.rest.utils.getMemberName
import net.corda.applications.workers.rest.utils.onboardStaticMembers
import net.corda.membership.rest.v1.CertificatesRestResource
import net.corda.membership.rest.v1.KeysRestResource
import net.corda.v5.crypto.KeySchemeCodes
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.*

class CertificateRestResourceTest {
    @TempDir
    lateinit var tempDir: Path

    private val cordaCluster = E2eClusterFactory.getE2eCluster().apply {
        addMembers((1..2).map { createTestMember("Member$it") })
        addMember(createTestMember("Notary", NOTARY))
        addMembers((3..4).map { createTestMember("Member$it") })
    }

    @Test
    fun `register members`() {
        onboardStaticGroup(tempDir)

        val member = cordaCluster.members.first()

        val key = cordaCluster.deprecatedGenerateKeyPairIfNotExists(member.holdingId, "test")

        cordaCluster.clusterHttpClientFor(CertificatesRestResource::class.java)
            .use { client ->
                client.start().proxy.generateCsr(
                    tenantId = member.holdingId,
                    keyId = key,
                    x500Name = member.name,
                    subjectAlternativeNames = null,
                    contextMap = null
                )
            }
    }

    private fun onboardStaticGroup(tempDir: Path): String {
        val groupId = UUID.randomUUID().toString()
        val groupPolicy = createStaticMemberGroupPolicyJson(
            getCa(),
            groupId,
            cordaCluster
        )

        cordaCluster.onboardStaticMembers(groupPolicy, tempDir)

        // Assert all members can see each other in their member lists
        val allMembers = cordaCluster.members
        allMembers.forEach {
            cordaCluster.assertAllMembersAreInMemberList(it, allMembers)
        }
        return groupId
    }

    private fun E2eCluster.createTestMember(
        namePrefix: String,
        role: E2eClusterMemberRole? = null
    ): E2eClusterMember {
        val memberName = getMemberName<StaticNetworkTest>(namePrefix)
        return role?.let {
            E2eClusterMember(memberName, it)
        } ?: E2eClusterMember(memberName)
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
