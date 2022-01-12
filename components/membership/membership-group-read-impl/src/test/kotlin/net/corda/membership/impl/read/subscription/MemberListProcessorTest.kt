package net.corda.membership.impl.read.subscription

import net.corda.data.crypto.wire.WireSignatureWithKey
import net.corda.data.membership.SignedMemberInfo
import net.corda.membership.conversion.PropertyConverterImpl
import net.corda.membership.conversion.toWire
import net.corda.membership.identity.EndpointInfoImpl
import net.corda.membership.identity.MGMContextImpl
import net.corda.membership.identity.MemberContextImpl
import net.corda.membership.identity.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.identity.MemberInfoExtension.Companion.IDENTITY_KEYS_KEY
import net.corda.membership.identity.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.identity.MemberInfoExtension.Companion.MEMBER_STATUS_PENDING
import net.corda.membership.identity.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.identity.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.identity.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.identity.MemberInfoExtension.Companion.PARTY_OWNING_KEY
import net.corda.membership.identity.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.identity.MemberInfoExtension.Companion.PROTOCOL_VERSION
import net.corda.membership.identity.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.identity.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.identity.MemberInfoExtension.Companion.STATUS
import net.corda.membership.identity.MemberInfoExtension.Companion.URL_KEY
import net.corda.membership.identity.MemberInfoExtension.Companion.groupId
import net.corda.membership.identity.MemberInfoImpl
import net.corda.membership.identity.converter.EndpointInfoConverter
import net.corda.membership.identity.converter.PublicKeyConverter
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.messaging.api.records.Record
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.membership.identity.EndpointInfo
import net.corda.v5.membership.identity.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.PublicKey
import java.time.Instant

class MemberListProcessorTest {
    companion object {
        private val keyEncodingService: KeyEncodingService = mock()
        private val knownKey: PublicKey = mock()
        private const val knownKeyAsString = "12345"
        private val modifiedTime = Instant.now()
        private val endpoints = listOf(
            EndpointInfoImpl("https://corda5.r3.com:10000", EndpointInfo.DEFAULT_PROTOCOL_VERSION),
            EndpointInfoImpl("https://corda5.r3.com:10001", 10)
        )
        private val identityKeys = listOf(knownKey, knownKey)
        private val converter = PropertyConverterImpl(
            listOf(
                EndpointInfoConverter(),
                PublicKeyConverter(keyEncodingService),
            )
        )
        private val signature = WireSignatureWithKey(
            ByteBuffer.wrap(byteArrayOf()),
            ByteBuffer.wrap(byteArrayOf()),
        )
        private lateinit var alice: MemberInfo
        private lateinit var bob: MemberInfo
        private lateinit var charlie: MemberInfo

        private lateinit var memberListProcessor: MemberListProcessor
        private lateinit var memberListFromTopic: Map<String, SignedMemberInfo>

        private val membershipGroupReadCache = MembershipGroupReadCache.Impl()

        @Suppress("SpreadOperator")
        private fun createTestMemberInfo(x500Name: String, status: String): MemberInfo = MemberInfoImpl(
            memberProvidedContext = MemberContextImpl(
                sortedMapOf(
                    PARTY_NAME to x500Name,
                    PARTY_OWNING_KEY to knownKeyAsString,
                    GROUP_ID to "DEFAULT_MEMBER_GROUP_ID",
                    *convertPublicKeys().toTypedArray(),
                    *convertEndpoints().toTypedArray(),
                    SOFTWARE_VERSION to "5.0.0",
                    PLATFORM_VERSION to "10",
                    SERIAL to "1",
                ),
                converter
            ),
            mgmProvidedContext = MGMContextImpl(
                sortedMapOf(
                    STATUS to status,
                    MODIFIED_TIME to modifiedTime.toString(),
                ),
                converter
            )
        )

        private fun convertToTestTopicData(memberInfoList: List<MemberInfo>): Map<String, SignedMemberInfo> {
            val topicData = mutableMapOf<String, SignedMemberInfo>()
            memberInfoList.forEach { member ->
                val id = HoldingIdentity(member.name.toString(), member.groupId).id
                topicData[id] = SignedMemberInfo(
                    member.memberProvidedContext.toWire(),
                    member.mgmProvidedContext.toWire(),
                    signature,
                    signature
                )
            }
            return topicData
        }

        private fun convertEndpoints(): List<Pair<String, String>> {
            val result = mutableListOf<Pair<String, String>>()
            for (index in endpoints.indices) {
                result.add(
                    Pair(
                        String.format(URL_KEY, index),
                        endpoints[index].url
                    )
                )
                result.add(
                    Pair(
                        String.format(PROTOCOL_VERSION, index),
                        endpoints[index].protocolVersion.toString()
                    )
                )
            }
            return result
        }

        private fun convertPublicKeys(): List<Pair<String, String>> =
            identityKeys.mapIndexed { index, identityKey ->
                String.format(
                    IDENTITY_KEYS_KEY,
                    index
                ) to keyEncodingService.encodeAsString(identityKey)
            }

        @JvmStatic
        @BeforeAll
        fun setUp() {
            memberListProcessor = MemberListProcessor(membershipGroupReadCache, keyEncodingService)
            whenever(keyEncodingService.decodePublicKey(knownKeyAsString)).thenReturn(knownKey)
            whenever(keyEncodingService.encodeAsString(knownKey)).thenReturn(knownKeyAsString)
            alice = createTestMemberInfo("O=Alice,L=London,C=GB", MEMBER_STATUS_PENDING)
            bob = createTestMemberInfo("O=Bob,L=London,C=GB", MEMBER_STATUS_ACTIVE)
            charlie = createTestMemberInfo("O=Charlie,L=London,C=GB", MEMBER_STATUS_SUSPENDED)
            memberListFromTopic = convertToTestTopicData(listOf(alice, bob, charlie))
        }
    }

    @Test
    fun `Key class is String`() {
        assertEquals(String::class.java, memberListProcessor.keyClass)
    }

    @Test
    fun `Value class is SignedMemberInfo`() {
        assertEquals(SignedMemberInfo::class.java, memberListProcessor.valueClass)
    }

    @Test
    fun `Member list cache is successfully populated from member list topic on initial snapshot`() {
        membershipGroupReadCache.start()
        memberListProcessor.onSnapshot(memberListFromTopic)
        listOf(alice, bob, charlie).forEach { member ->
            assertEquals(
                listOf(member),
                membershipGroupReadCache.memberListCache.get(HoldingIdentity(member.name.toString(), member.groupId))
            )
        }
        membershipGroupReadCache.stop()
    }

    @Test
    fun `Member list cache is successfully updated with new record`() {
        membershipGroupReadCache.start()
        memberListProcessor.onSnapshot(memberListFromTopic)
        val newMember = createTestMemberInfo("O=NewMember,L=London,C=GB", MEMBER_STATUS_ACTIVE)
        val topicData = convertToTestTopicData(listOf(newMember)).entries.first()
        val newRecord = Record("dummy-topic", topicData.key, topicData.value)
        memberListProcessor.onNext(newRecord, null, memberListFromTopic)
        assertEquals(
            listOf(newMember),
            membershipGroupReadCache.memberListCache.get(HoldingIdentity(newMember.name.toString(), newMember.groupId))
        )
        membershipGroupReadCache.stop()
    }

    @Test
    fun `Member list cache is successfully updated with changed record`() {
        membershipGroupReadCache.start()
        memberListProcessor.onSnapshot(memberListFromTopic)
        val updatedAlice = createTestMemberInfo("O=Alice,L=London,C=GB", MEMBER_STATUS_ACTIVE)
        val topicData = convertToTestTopicData(listOf(updatedAlice)).entries.first()
        val newRecord = Record("dummy-topic", topicData.key, topicData.value)
        val oldValue = SignedMemberInfo(
            alice.memberProvidedContext.toWire(),
            alice.mgmProvidedContext.toWire(),
            signature,
            signature
        )
        memberListProcessor.onNext(newRecord, oldValue, memberListFromTopic)
        val updatedAliceId = HoldingIdentity(updatedAlice.name.toString(), updatedAlice.groupId)
        assertEquals(
            listOf(updatedAlice),
            membershipGroupReadCache.memberListCache.get(updatedAliceId)
        )
        assertNotEquals(
            listOf(alice),
            membershipGroupReadCache.memberListCache.get(updatedAliceId)
        )
        membershipGroupReadCache.stop()
    }
}
