package net.corda.membership.staticmemberlist

import net.corda.crypto.CryptoLibraryFactory
import net.corda.crypto.SigningService
import net.corda.membership.CPIWhiteList
import net.corda.membership.GroupPolicy
import net.corda.membership.conversion.PropertyConverterImpl
import net.corda.membership.identity.EndpointInfoImpl
import net.corda.membership.identity.MGMContextImpl
import net.corda.membership.identity.MemberContextImpl
import net.corda.membership.identity.MemberInfoExtension
import net.corda.membership.identity.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.identity.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
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
import net.corda.membership.staticmemberlist.StaticMemberTemplateExtension.Companion.ENDPOINT_PROTOCOL
import net.corda.membership.staticmemberlist.StaticMemberTemplateExtension.Companion.ENDPOINT_URL
import net.corda.membership.staticmemberlist.StaticMemberTemplateExtension.Companion.MEMBER_STATUS
import net.corda.membership.staticmemberlist.StaticMemberTemplateExtension.Companion.ROTATED_KEY_ALIAS
import net.corda.membership.staticmemberlist.StaticMemberTemplateExtension.Companion.STATIC_MODIFIED_TIME
import net.corda.membership.staticmemberlist.StaticMemberTemplateExtension.Companion.STATIC_PLATFORM_VERSION
import net.corda.membership.staticmemberlist.StaticMemberTemplateExtension.Companion.STATIC_SERIAL
import net.corda.membership.staticmemberlist.StaticMemberTemplateExtension.Companion.STATIC_SOFTWARE_VERSION
import net.corda.membership.staticmemberlist.StaticMemberTemplateExtension.Companion.NAME
import net.corda.membership.staticmemberlist.StaticMemberTemplateExtension.Companion.staticMembers
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
    private val cryptoLibraryFactory: CryptoLibraryFactory,
    private val signingService: SigningService
) : MembershipGroupReader {

    private val keyEncodingService = cryptoLibraryFactory.getKeyEncodingService()
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

    private fun parseMemberTemplate(): List<MemberInfo> {
        val members = mutableListOf<MemberInfo>()
        val converter = PropertyConverterImpl(
            listOf(
                EndpointInfoConverter(),
                PublicKeyConverter(cryptoLibraryFactory),
            )
        )
        @Suppress("SpreadOperator")
        policy.staticMembers.forEach { member ->
            val owningKey = signingService.generateKeyPair(member["keyAlias"].toString())
            members.add(
                MemberInfoImpl(
                    memberProvidedContext = MemberContextImpl(
                        sortedMapOf(
                            PARTY_NAME to member[NAME].toString(),
                            PARTY_OWNING_KEY to keyEncodingService.encodeAsString(owningKey),
                            GROUP_ID to policy.groupId,
                            *convertPublicKeys(keyEncodingService, member, owningKey).toTypedArray(),
                            *convertEndpoints(member).toTypedArray(),
                            SOFTWARE_VERSION to (member[STATIC_SOFTWARE_VERSION] ?: "5.0.0"),
                            PLATFORM_VERSION to (member[STATIC_PLATFORM_VERSION] ?: "10"),
                            SERIAL to (member[STATIC_SERIAL] ?: "1"),
                        ),
                        converter
                    ),
                    mgmProvidedContext = MGMContextImpl(
                        sortedMapOf(
                            STATUS to (member[MEMBER_STATUS] ?: MEMBER_STATUS_ACTIVE),
                            MODIFIED_TIME to (member[STATIC_MODIFIED_TIME] ?: Instant.now().toString()),
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
        val endpointUrlIdentifier = ENDPOINT_URL.substringBefore("-")
        member.keys.filter { it.startsWith(endpointUrlIdentifier) }.size.apply {
            for (index in 1..this) {
                endpoints.add(
                    EndpointInfoImpl(
                        member[String.format(ENDPOINT_URL, index)].toString(),
                        member[String.format(ENDPOINT_PROTOCOL, index)]!!.toInt()
                    )
                )
            }
        }
        val result = mutableListOf<Pair<String, String>>()
        for (index in endpoints.indices) {
            result.add(
                Pair(
                    String.format(MemberInfoExtension.URL_KEY, index),
                    endpoints[index].url)
            )
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
        val historicKeyIdentifier = ROTATED_KEY_ALIAS.substringBefore("-")
        member.keys.filter { it.startsWith(historicKeyIdentifier) }.forEach { key ->
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
