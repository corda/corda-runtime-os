package net.corda.p2p.linkmanager.utilities

import net.corda.crypto.cipher.suite.PublicKeyHash
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.MemberInfoExtension.Companion.ENDPOINTS
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.p2p.crypto.protocol.ProtocolConstants
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.EndpointInfo
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey

fun mockMembersAndGroups(
    vararg members: HoldingIdentity
): Pair<MembershipGroupReaderProvider, GroupPolicyProvider> {
    return mockMembers(members.toList()) to mockGroups(members.toList())
}

fun mockMemberInfo(
    holdingIdentity: HoldingIdentity,
    endPoint: String,
    publicKey: PublicKey,
): MemberInfo {
    val endpoints = mock<EndpointInfo> {
        on { url } doReturn endPoint
        on { protocolVersion } doReturn ProtocolConstants.PROTOCOL_VERSION
    }
    val context = mock<MemberContext> {
        on { parse(GROUP_ID, String::class.java) } doReturn holdingIdentity.groupId
        on { parseList(ENDPOINTS, EndpointInfo::class.java) } doReturn listOf(endpoints)
    }
    return mock {
        on { memberProvidedContext } doReturn context
        on { name } doReturn holdingIdentity.x500Name
        on { sessionInitiationKey } doReturn publicKey
    }
}

fun mockMembers(members: Collection<HoldingIdentity>): MembershipGroupReaderProvider {
    val endpoint = "https://10.0.0.1/"

    val provider = BouncyCastleProvider()
    val keyPairGenerator = KeyPairGenerator.getInstance("EC", provider)
    val messageDigest = MessageDigest.getInstance(ProtocolConstants.HASH_ALGO, provider)
    val identities = members.associateWith { holdingId ->
        val keyPair = keyPairGenerator.generateKeyPair()
        mockMemberInfo(
            holdingId,
            endpoint,
            keyPair.public
        )
    }
    fun MessageDigest.hash(data: ByteArray): ByteArray {
        this.reset()
        this.update(data)
        return digest()
    }
    val hashToInfo = identities.values.associateBy {
        val publicKeyHash = PublicKeyHash.parse(messageDigest.hash(it.sessionInitiationKey.encoded))
        publicKeyHash
    }
    return object : MembershipGroupReaderProvider {
        override fun getGroupReader(holdingIdentity: HoldingIdentity): MembershipGroupReader {
            return mock {
                on { lookup(any()) } doAnswer {
                    val name = it.arguments[0] as MemberX500Name
                    identities[HoldingIdentity(name, holdingIdentity.groupId)]
                }
                on { lookupBySessionKey(any()) } doAnswer {
                    val key = it.arguments[0] as PublicKeyHash
                    hashToInfo[key]
                }
            }
        }

        override val isRunning = true

        override fun start() {
        }

        override fun stop() {
        }

    }
}

fun mockGroups(holdingIdentities: Collection<HoldingIdentity>): GroupPolicyProvider {
    return object : GroupPolicyProvider {

        override fun getGroupPolicy(holdingIdentity: HoldingIdentity): GroupPolicy? {
            return if (holdingIdentities.contains(holdingIdentity)) {
                val parameters = mock<GroupPolicy.P2PParameters> {
                    on { tlsPki } doReturn GroupPolicyConstants.PolicyValues.P2PParameters.TlsPkiMode.STANDARD
                }
                mock {
                    on { p2pParameters } doReturn parameters
                }

            } else {
                null
            }
        }

        override fun registerListener(name: String, callback: (HoldingIdentity, GroupPolicy) -> Unit) {
        }

        override val isRunning = true

        override fun start() {
        }

        override fun stop() {
        }
    }
}
