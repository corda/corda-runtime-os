package net.corda.membership.staticmemberlist

import net.corda.crypto.SigningService
import net.corda.membership.CPIWhiteList
import net.corda.membership.GroupPolicy
import net.corda.membership.conversion.PropertyConverterImpl
import net.corda.membership.identity.EndpointInfoImpl
import net.corda.membership.identity.MGMContextImpl
import net.corda.membership.identity.MemberContextImpl
import net.corda.membership.identity.MemberInfoExtension
import net.corda.membership.identity.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.identity.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.identity.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.identity.MemberInfoExtension.Companion.PARTY_OWNING_KEY
import net.corda.membership.identity.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.identity.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.identity.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.identity.MemberInfoExtension.Companion.STATUS
import net.corda.membership.identity.MemberInfoImpl
import net.corda.membership.identity.converter.EndpointInfoConverter
import net.corda.membership.identity.converter.PublicKeyConverter
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.staticmemberlist.GroupPolicyExtension.Companion.staticMemberTemplate
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.membership.GroupParameters
import net.corda.v5.membership.identity.EndpointInfo
import net.corda.v5.membership.identity.MemberInfo
import net.corda.v5.membership.identity.MemberX500Name
import java.security.PublicKey
import java.time.Instant

class MembershipGroupReaderImpl(
    override val owningMember: MemberX500Name,
    override val policy: GroupPolicy,
    private val keyEncodingService: KeyEncodingService,
    private val signingService: SigningService
) : MembershipGroupReader {

    private val memberInfoList: List<MemberInfo> = parseMemberTemplate()

    override val groupId: String
        get() = policy.groupId

    override val groupParameters: GroupParameters
        get() = TODO("Not yet implemented")

    override val cpiWhiteList: CPIWhiteList
        get() = TODO("Not yet implemented")

    override fun lookup(publicKeyHash: ByteArray): MemberInfo? =
        memberInfoList.find { it.identityKeys.contains(keyEncodingService.decodePublicKey(publicKeyHash)) }

    override fun lookup(name: MemberX500Name): MemberInfo? = memberInfoList.find { it.name == name }

    override fun getHistoricGroupParameters(version: String): GroupParameters? {
        TODO("Not yet implemented")
    }

    private fun parseMemberTemplate(): List<MemberInfo> {
        val members = mutableListOf<MemberInfo>()
        val converter = PropertyConverterImpl(
            listOf(
                EndpointInfoConverter(),
                PublicKeyConverter(keyEncodingService),
            )
        )
        @Suppress("SpreadOperator")
        policy.staticMemberTemplate.forEach { member ->
            val owningKey = signingService.generateKeyPair(member["keyAlias"].toString())
            members.add(
                MemberInfoImpl(
                    memberProvidedContext = MemberContextImpl(
                        sortedMapOf(
                            PARTY_NAME to member["x500Name"].toString(),
                            PARTY_OWNING_KEY to keyEncodingService.encodeAsString(owningKey),
                            GROUP_ID to policy.groupId,
                            *convertPublicKeys(keyEncodingService, member, owningKey).toTypedArray(),
                            *convertEndpoints(member).toTypedArray(),
                            SOFTWARE_VERSION to "5.0.0",
                            PLATFORM_VERSION to "10",
                            SERIAL to "1",
                        ),
                        converter
                    ),
                    mgmProvidedContext = MGMContextImpl(
                        sortedMapOf(
                            STATUS to member["memberStatus"].toString(),
                            MODIFIED_TIME to Instant.now().toString(),
                        ),
                        converter
                    )
                )
            )
        }
        return members
    }

    private fun convertEndpoints(member: Map<String, String>): List<Pair<String, String>> {
        val endpoints = mutableListOf<EndpointInfo>()
        member.keys.filter { it.startsWith("endpointUrl") }.size.apply {
            for (index in 1..this) {
                endpoints.add(
                    EndpointInfoImpl(
                        member["endpointUrl-$index"].toString(),
                        member["endpointProtocol-$index"]!!.toInt()
                    )
                )
            }
        }
        val result = mutableListOf<Pair<String, String>>()
        for (index in endpoints.indices) {
            result.add(Pair(String.format(MemberInfoExtension.URL_KEY, index), endpoints[index].url))
            result.add(
                Pair(
                    String.format(MemberInfoExtension.PROTOCOL_VERSION, index),
                    endpoints[index].protocolVersion.toString()
                )
            )
        }
        return result
    }

    private fun convertPublicKeys(
        keyEncodingService: KeyEncodingService,
        member: Map<String, String>,
        owningKey: PublicKey
    ): List<Pair<String, String>> {
        val identityKeys = mutableListOf(owningKey)
        member.keys.filter { it.startsWith("rotatedKeyAlias") }.forEach { key ->
            identityKeys.add(signingService.generateKeyPair(member[key].toString()))
        }
        return identityKeys.mapIndexed { index, identityKey ->
            String.format(
                MemberInfoExtension.IDENTITY_KEYS_KEY,
                index
            ) to keyEncodingService.encodeAsString(identityKey)
        }
    }
}
