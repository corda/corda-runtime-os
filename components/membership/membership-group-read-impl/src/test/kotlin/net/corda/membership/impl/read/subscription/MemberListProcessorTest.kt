package net.corda.membership.impl.read.subscription

import net.corda.crypto.CryptoLibraryFactory
import net.corda.data.crypto.wire.WireSignatureWithKey
import net.corda.data.membership.PersistentMemberInfo
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
import net.corda.virtualnode.toAvro
import org.junit.jupiter.api.Assertions.assertEquals
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
        private val cryptoLibraryFactory: CryptoLibraryFactory = mock<CryptoLibraryFactory>().apply {
            whenever(getKeyEncodingService()).thenReturn(keyEncodingService)
        }
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
                PublicKeyConverter(cryptoLibraryFactory),
            )
        )
        private val signature = WireSignatureWithKey(
            ByteBuffer.wrap(byteArrayOf()),
            ByteBuffer.wrap(byteArrayOf()),
        )
        private lateinit var alice: MemberInfo
        private lateinit var aliceIdentity: HoldingIdentity
        private lateinit var bob: MemberInfo
        private lateinit var bobIdentity: HoldingIdentity
        private lateinit var charlie: MemberInfo
        private lateinit var charlieIdentity: HoldingIdentity

        private lateinit var memberListProcessor: MemberListProcessor
        private lateinit var memberListFromTopic: Map<String, PersistentMemberInfo>

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

        private fun convertToTestTopicData(
            memberInfoList: List<MemberInfo>,
            selfOwned: Boolean = false
        ): Map<String, PersistentMemberInfo> {
            val topicData = mutableMapOf<String, PersistentMemberInfo>()
            memberInfoList.forEach { member ->
                val holdingIdentity = HoldingIdentity(member.name.toString(), member.groupId)
                val signedMemberInfo = SignedMemberInfo(
                    member.memberProvidedContext.toWire(),
                    member.mgmProvidedContext.toWire(),
                    signature,
                    signature
                )
                if (!selfOwned && holdingIdentity != aliceIdentity) {
                    topicData[aliceIdentity.id + holdingIdentity.id] = PersistentMemberInfo(aliceIdentity.toAvro(), signedMemberInfo)
                }
                topicData[holdingIdentity.id] = PersistentMemberInfo(holdingIdentity.toAvro(), signedMemberInfo)
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
            memberListProcessor = MemberListProcessor(membershipGroupReadCache, converter)
            whenever(keyEncodingService.decodePublicKey(knownKeyAsString)).thenReturn(knownKey)
            whenever(keyEncodingService.encodeAsString(knownKey)).thenReturn(knownKeyAsString)
            alice = createTestMemberInfo("O=Alice,L=London,C=GB", MEMBER_STATUS_PENDING)
            aliceIdentity = HoldingIdentity(alice.name.toString(), alice.groupId)
            bob = createTestMemberInfo("O=Bob,L=London,C=GB", MEMBER_STATUS_ACTIVE)
            bobIdentity = HoldingIdentity(bob.name.toString(), bob.groupId)
            charlie = createTestMemberInfo("O=Charlie,L=London,C=GB", MEMBER_STATUS_SUSPENDED)
            charlieIdentity = HoldingIdentity(charlie.name.toString(), charlie.groupId)
            memberListFromTopic = convertToTestTopicData(listOf(alice, bob, charlie))
        }
    }

    @Test
    fun `Key class is String`() {
        assertEquals(String::class.java, memberListProcessor.keyClass)
    }

    @Test
    fun `Value class is SignedMemberInfo`() {
        assertEquals(PersistentMemberInfo::class.java, memberListProcessor.valueClass)
    }

    @Test
    fun `Member list cache is successfully populated from member list topic on initial snapshot`() {
        membershipGroupReadCache.start()
        memberListProcessor.onSnapshot(memberListFromTopic)
        assertEquals(listOf(alice, bob, charlie), membershipGroupReadCache.memberListCache.get(aliceIdentity))
        assertEquals(listOf(bob), membershipGroupReadCache.memberListCache.get(bobIdentity))
        assertEquals(listOf(charlie), membershipGroupReadCache.memberListCache.get(charlieIdentity))
        membershipGroupReadCache.stop()
    }

    @Test
    fun `Member list cache is successfully updated with new record`() {
        membershipGroupReadCache.start()
        memberListProcessor.onSnapshot(memberListFromTopic)
        val newMember = createTestMemberInfo("O=NewMember,L=London,C=GB", MEMBER_STATUS_ACTIVE)
        val newMemberIdentity = HoldingIdentity(newMember.name.toString(), newMember.groupId)
        val topicData = convertToTestTopicData(listOf(newMember), true).entries.first()
        val newRecord = Record("dummy-topic", topicData.key, topicData.value)
        memberListProcessor.onNext(newRecord, null, memberListFromTopic)
        assertEquals(listOf(newMember), membershipGroupReadCache.memberListCache.get(newMemberIdentity))
        membershipGroupReadCache.stop()
    }

    @Test
    fun `Member list cache is successfully updated with changed record`() {
        membershipGroupReadCache.start()
        memberListProcessor.onSnapshot(memberListFromTopic)
        val updatedAlice = createTestMemberInfo("O=Alice,L=London,C=GB", MEMBER_STATUS_ACTIVE)
        val topicData = convertToTestTopicData(listOf(updatedAlice), true).entries.first()
        val newRecord = Record("dummy-topic", topicData.key, topicData.value)
        val oldValue = PersistentMemberInfo(
            aliceIdentity.toAvro(),
            SignedMemberInfo(
                alice.memberProvidedContext.toWire(),
                alice.mgmProvidedContext.toWire(),
                signature,
                signature
            )
        )
        memberListProcessor.onNext(newRecord, oldValue, memberListFromTopic)
        assertEquals(
            listOf(bob, charlie, updatedAlice),
            membershipGroupReadCache.memberListCache.get(aliceIdentity)
        )
        membershipGroupReadCache.stop()
    }
}
