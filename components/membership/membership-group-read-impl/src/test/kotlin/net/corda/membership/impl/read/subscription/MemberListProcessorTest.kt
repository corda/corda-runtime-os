package net.corda.membership.impl.read.subscription

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.impl.converter.PublicKeyConverter
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.SignedData
import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.layeredpropertymap.avro.toAvro
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.membership.lib.EndpointInfoFactory
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEYS_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_PENDING
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEYS
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.PROTOCOL_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.lib.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoExtension.Companion.URL_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.impl.MemberInfoFactoryImpl
import net.corda.membership.lib.impl.converter.EndpointInfoConverter
import net.corda.membership.lib.impl.converter.MemberNotaryDetailsConverter
import net.corda.messaging.api.records.Record
import net.corda.test.util.time.TestClock
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.security.PublicKey
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class MemberListProcessorTest {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private val clock = TestClock(Instant.ofEpochSecond(100))
        private val keyEncodingService: CipherSchemeMetadata = mock()
        private val knownKey: PublicKey = mock()
        private const val knownKeyAsString = "12345"
        private val modifiedTime = clock.instant()
        private val endpointInfoFactory: EndpointInfoFactory = mock {
            on { create(any(), any()) } doAnswer { invocation ->
                mock {
                    on { this.url } doReturn invocation.getArgument(0)
                    on { this.protocolVersion } doReturn invocation.getArgument(1)
                }
            }
        }
        private val endpoints = listOf(
            endpointInfoFactory.create("https://corda5.r3.com:10000"),
            endpointInfoFactory.create("https://corda5.r3.com:10001", 10)
        )
        private val ledgerKeys = listOf(knownKey, knownKey)
        private lateinit var memberInfoFactory: MemberInfoFactory
        private val converters = listOf(
            EndpointInfoConverter(),
            MemberNotaryDetailsConverter(keyEncodingService),
            PublicKeyConverter(keyEncodingService),
        )

        private lateinit var alice: MemberInfo
        private lateinit var aliceIdentity: HoldingIdentity
        private lateinit var bob: MemberInfo
        private lateinit var bobIdentity: HoldingIdentity
        private lateinit var charlie: MemberInfo
        private lateinit var charlieIdentity: HoldingIdentity

        private val keyValuePairListSerializer = mock<CordaAvroSerializer<KeyValuePairList>>()
        private val keyValuePairListDeserializer = mock<CordaAvroDeserializer<KeyValuePairList>>()
        private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
            on {
                createAvroDeserializer(
                    any(),
                    eq(KeyValuePairList::class.java)
                )
            } doReturn keyValuePairListDeserializer
            on {
                createAvroSerializer<KeyValuePairList>(any())
            } doReturn keyValuePairListSerializer
        }

        private lateinit var memberListProcessor: MemberListProcessor
        private lateinit var memberListFromTopic: Map<String, PersistentMemberInfo>

        private lateinit var membershipGroupReadCache: MembershipGroupReadCache

        @Suppress("SpreadOperator")
        private fun createTestMemberInfo(x500Name: String, status: String): MemberInfo = memberInfoFactory.createMemberInfo(
            sortedMapOf(
                PARTY_NAME to x500Name,
                String.format(PARTY_SESSION_KEYS, 0) to knownKeyAsString,
                GROUP_ID to "DEFAULT_MEMBER_GROUP_ID",
                *convertPublicKeys().toTypedArray(),
                *convertEndpoints().toTypedArray(),
                SOFTWARE_VERSION to "5.0.0",
                PLATFORM_VERSION to "5000",
            ),
            sortedMapOf(
                STATUS to status,
                MODIFIED_TIME to modifiedTime.toString(),
                SERIAL to "1",
            )

        )

        private fun convertToTestTopicData(
            memberInfoList: List<MemberInfo>,
            selfOwned: Boolean = false
        ): Map<String, PersistentMemberInfo> {
            val topicData = mutableMapOf<String, PersistentMemberInfo>()
            memberInfoList.forEach { member ->
                val holdingIdentity = HoldingIdentity(member.name, member.groupId)
                if (!selfOwned && holdingIdentity != aliceIdentity) {
                    topicData[aliceIdentity.shortHash.value + holdingIdentity.shortHash.value] = PersistentMemberInfo(
                        aliceIdentity.toAvro(),
                        null,
                        null,
                        SignedData(
                            member.memberProvidedContext.toAvro().toByteBuffer(),
                            CryptoSignatureWithKey(ByteBuffer.wrap(byteArrayOf()), ByteBuffer.wrap(byteArrayOf())),
                            CryptoSignatureSpec("", null, null),
                        ),
                        member.mgmProvidedContext.toAvro().toByteBuffer(),
                    )
                }
                topicData[holdingIdentity.shortHash.value] = PersistentMemberInfo(
                    holdingIdentity.toAvro(),
                    null,
                    null,
                    SignedData(
                        member.memberProvidedContext.toAvro().toByteBuffer(),
                        CryptoSignatureWithKey(ByteBuffer.wrap(byteArrayOf()), ByteBuffer.wrap(byteArrayOf())),
                        CryptoSignatureSpec("", null, null),
                    ),
                    member.mgmProvidedContext.toAvro().toByteBuffer(),
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
            ledgerKeys.mapIndexed { index, ledgerKey ->
                String.format(
                    LEDGER_KEYS_KEY,
                    index
                ) to keyEncodingService.encodeAsString(ledgerKey)
            }

        private fun mockSerializer(members: List<MemberInfo>) = members.forEach { member ->
            whenever(
                keyValuePairListDeserializer.deserialize(member.mgmProvidedContext.toAvro().toByteBuffer().array())
            ).thenReturn(member.mgmProvidedContext.toAvro())
            whenever(
                keyValuePairListDeserializer.deserialize(member.memberProvidedContext.toAvro().toByteBuffer().array())
            ).thenReturn(member.memberProvidedContext.toAvro())
        }

        private val timesCallbackCalled = AtomicInteger(0)
        private fun test(cache: MembershipGroupReadCache) {
            logger.info("OnSnapshot finished and ${cache.javaClass.name} is ready to use.")
            timesCallbackCalled.incrementAndGet()
        }

        @JvmStatic
        @BeforeAll
        fun setUp() {
            memberInfoFactory = MemberInfoFactoryImpl(
                LayeredPropertyMapMocks.createFactory(converters),
                cordaAvroSerializationFactory
            )
            membershipGroupReadCache = MembershipGroupReadCache.Impl()
            memberListProcessor = MemberListProcessor(membershipGroupReadCache, memberInfoFactory, ::test)
            whenever(keyEncodingService.decodePublicKey(knownKeyAsString)).thenReturn(knownKey)
            whenever(keyEncodingService.encodeAsString(knownKey)).thenReturn(knownKeyAsString)
            alice = createTestMemberInfo("O=Alice,L=London,C=GB", MEMBER_STATUS_PENDING)
            aliceIdentity = HoldingIdentity(alice.name, alice.groupId)
            bob = createTestMemberInfo("O=Bob,L=London,C=GB", MEMBER_STATUS_ACTIVE)
            bobIdentity = HoldingIdentity(bob.name, bob.groupId)
            charlie = createTestMemberInfo("O=Charlie,L=London,C=GB", MEMBER_STATUS_SUSPENDED)
            charlieIdentity = HoldingIdentity(charlie.name, charlie.groupId)
            memberListFromTopic = convertToTestTopicData(listOf(alice, bob, charlie))
            mockSerializer(listOf(alice, bob, charlie))
        }
    }

    @BeforeEach
    fun reset() {
        timesCallbackCalled.set(0)
    }

    @AfterEach
    fun tearDown() {
        membershipGroupReadCache.clear()
    }

    @Test
    fun `Key class is String`() {
        assertEquals(String::class.java, memberListProcessor.keyClass)
    }

    @Test
    fun `Value class is PersistentMemberInfo`() {
        assertEquals(PersistentMemberInfo::class.java, memberListProcessor.valueClass)
    }

    @Test
    fun `Member list cache is successfully populated from member list topic on initial snapshot`() {
        memberListProcessor.onSnapshot(memberListFromTopic)
        assertEquals(listOf(alice, bob, charlie), membershipGroupReadCache.memberListCache.get(aliceIdentity))
        assertEquals(listOf(bob), membershipGroupReadCache.memberListCache.get(bobIdentity))
        assertEquals(listOf(charlie), membershipGroupReadCache.memberListCache.get(charlieIdentity))
        assertThat(timesCallbackCalled.get()).isEqualTo(1)
    }

    @Test
    fun `Member list cache is successfully updated with new record`() {
        memberListProcessor.onSnapshot(memberListFromTopic)
        val newMember = createTestMemberInfo("O=NewMember,L=London,C=GB", MEMBER_STATUS_ACTIVE)
        val newMemberIdentity = HoldingIdentity(newMember.name, newMember.groupId)
        mockSerializer(listOf(newMember))
        val topicData = convertToTestTopicData(listOf(newMember), true).entries.first()
        val newRecord = Record("dummy-topic", topicData.key, topicData.value)
        memberListProcessor.onNext(newRecord, null, memberListFromTopic)
        assertEquals(listOf(newMember), membershipGroupReadCache.memberListCache.get(newMemberIdentity))
    }

    @Test
    fun `Member list cache is successfully updated with changed record`() {
        memberListProcessor.onSnapshot(memberListFromTopic)
        val updatedAlice = createTestMemberInfo(aliceIdentity.x500Name.toString(), MEMBER_STATUS_PENDING)
        mockSerializer(listOf(updatedAlice))
        val topicData = convertToTestTopicData(listOf(updatedAlice), true).entries.first()
        val newRecord = Record("dummy-topic", topicData.key, topicData.value)
        val oldValue = PersistentMemberInfo(
            aliceIdentity.toAvro(),
            null,
            null,
            SignedData(
                alice.memberProvidedContext.toAvro().toByteBuffer(),
                CryptoSignatureWithKey(ByteBuffer.wrap(byteArrayOf()), ByteBuffer.wrap(byteArrayOf())),
                CryptoSignatureSpec("", null, null),
            ),
            alice.mgmProvidedContext.toAvro().toByteBuffer(),
        )
        memberListProcessor.onNext(newRecord, oldValue, memberListFromTopic)
        assertThat(membershipGroupReadCache.memberListCache.get(aliceIdentity))
            .containsExactlyInAnyOrder(bob, charlie, updatedAlice)
    }

    @Test
    fun `Member list cache is successfully replaced with changed record`() {
        val oldAlice = createTestMemberInfo(aliceIdentity.x500Name.toString(), MEMBER_STATUS_ACTIVE)
        val memberList = convertToTestTopicData(listOf(oldAlice), true)
        memberListProcessor.onSnapshot(memberList)
        val updatedAlice = createTestMemberInfo(aliceIdentity.x500Name.toString(), MEMBER_STATUS_ACTIVE)
        mockSerializer(listOf(updatedAlice))
        val topicData = convertToTestTopicData(listOf(updatedAlice), true).entries.first()
        val newRecord = Record("dummy-topic", topicData.key, topicData.value)
        val oldValue = PersistentMemberInfo(
            aliceIdentity.toAvro(),
            null,
            null,
            SignedData(
                oldAlice.memberProvidedContext.toAvro().toByteBuffer(),
                CryptoSignatureWithKey(ByteBuffer.wrap(byteArrayOf()), ByteBuffer.wrap(byteArrayOf())),
                CryptoSignatureSpec("", null, null),
            ),
            oldAlice.mgmProvidedContext.toAvro().toByteBuffer(),
        )
        memberListProcessor.onNext(newRecord, oldValue, memberList)
        assertThat(membershipGroupReadCache.memberListCache.get(aliceIdentity))
            .containsExactly(updatedAlice)
    }
}
