package net.corda.membership.impl.registration.staticnetwork.cache

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.SignedGroupParameters
import net.corda.data.membership.staticgroup.StaticGroupDefinition
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.lib.EPOCH_KEY
import net.corda.membership.lib.MODIFIED_TIME_KEY
import net.corda.membership.lib.MPV_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_ROLE
import net.corda.membership.lib.MemberInfoExtension.Companion.ROLES_PREFIX
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.notary.MemberNotaryDetails
import net.corda.membership.lib.notary.MemberNotaryKey
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

class GroupParametersCacheTest {
    private companion object {
        const val MPV = 5000
        const val EPOCH = 1
        const val KNOWN_NOTARY_SERVICE = "O=NotaryA, L=LDN, C=GB"
        const val KNOWN_NOTARY_PLUGIN = "net.corda.notary.MyNotaryService"
    }

    private val clock = TestClock(Instant.ofEpochSecond(500))

    private val knownGroupId = UUID.randomUUID().toString()
    private val knownIdentity = HoldingIdentity(MemberX500Name.parse("CN=Bob, O=Bob Corp, L=LDN, C=GB"), knownGroupId)
    private val platformInfoProvider: PlatformInfoProvider = mock {
        on { activePlatformVersion } doReturn MPV
    }
    private val pubKeyBytes = "test-key".toByteArray()
    private val keyEncodingService = mock<KeyEncodingService> {
        on { encodeAsString(any()) } doReturn "test-key"
        on { encodeAsByteArray(any()) } doReturn pubKeyBytes
    }
    private val publishCaptor = argumentCaptor<List<Record<*, *>>>()
    private val publisher: Publisher = mock {
        on { publish(publishCaptor.capture()) } doReturn listOf(CompletableFuture.completedFuture(Unit))
    }


    private val paramCaptor = argumentCaptor<KeyValuePairList>()
    private val serializedParams = "group-params".toByteArray()
    private val deserializedParams = KeyValuePairList(
        mutableListOf(
            KeyValuePair(EPOCH_KEY, "1"),
            KeyValuePair(MPV_KEY, "5000"),
            KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString())
        )
    )
    private val serializer: CordaAvroSerializer<KeyValuePairList> = mock {
        on { serialize(paramCaptor.capture()) } doReturn serializedParams
    }
    private val deserializer: CordaAvroDeserializer<KeyValuePairList> = mock {
        on { deserialize(serializedParams) } doReturn deserializedParams
    }

    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory = mock {
        on { createAvroDeserializer(any(), eq(KeyValuePairList::class.java)) } doReturn deserializer
        on { createAvroSerializer<KeyValuePairList>(any()) } doReturn serializer
    }

    private val signedGroupParameters = mock<SignedGroupParameters> {
        on { groupParameters } doReturn ByteBuffer.wrap(serializedParams)
    }

    @Test
    fun `group parameters are added to cache when set is called`() {
        val groupParameters = KeyValuePairList(
            mutableListOf(
                KeyValuePair(EPOCH_KEY, EPOCH.toString()),
                KeyValuePair("corda.notary.service.5.name", KNOWN_NOTARY_SERVICE),
                KeyValuePair("corda.notary.service.5.plugin", KNOWN_NOTARY_PLUGIN),
                KeyValuePair("corda.notary.service.5.keys.0", "existing-test-key"),
            )
        )
        whenever(deserializer.deserialize(serializedParams)).doReturn(groupParameters)
        val groupParametersCache = GroupParametersCache(
            platformInfoProvider,
            publisher,
            keyEncodingService,
            cordaAvroSerializationFactory,
            clock
        )

        groupParametersCache.set(knownGroupId, signedGroupParameters)

        assertThat(groupParametersCache.getOrCreateGroupParameters(knownIdentity)).isEqualTo(signedGroupParameters)
    }

    @Test
    fun `when cache is empty, getOrCreateGroupParameters publishes snapshot`() {
        val groupParametersCache = GroupParametersCache(
            platformInfoProvider,
            publisher,
            keyEncodingService,
            cordaAvroSerializationFactory,
            clock
        )

        groupParametersCache.getOrCreateGroupParameters(knownIdentity)

        with(paramCaptor.firstValue) {
            assertThat(items).containsExactlyElementsOf(
                listOf(
                    KeyValuePair(EPOCH_KEY, "1"),
                    KeyValuePair(MPV_KEY, "5000"),
                    KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString()),
                )
            )
        }
        with(publishCaptor.firstValue.first()) {
            assertThat(key).isEqualTo(knownGroupId)
            assertThat(value).isInstanceOf(StaticGroupDefinition::class.java)

            with(value as StaticGroupDefinition) {
                assertThat(groupParameters.groupParameters).isEqualTo(ByteBuffer.wrap(serializedParams))
            }
        }
    }

    @Test
    fun `addNotary called with new notary service name adds new notary service`() {
        val knownKey = mock<MemberNotaryKey> {
            on { publicKey } doReturn mock()
        }
        val notaryDetails = mock<MemberNotaryDetails> {
            on { keys } doReturn listOf(knownKey)
            on { serviceName } doReturn MemberX500Name.parse(KNOWN_NOTARY_SERVICE)
            on { servicePlugin } doReturn KNOWN_NOTARY_PLUGIN
        }
        val memberContext: MemberContext = mock {
            on { entries } doReturn mapOf("${ROLES_PREFIX}.0" to NOTARY_ROLE).entries
            on { parse(eq("corda.notary"), eq(MemberNotaryDetails::class.java)) } doReturn notaryDetails
        }
        val mgmContext: MGMContext = mock {
            on { entries } doReturn mapOf("a" to "b").entries
        }
        val notary: MemberInfo = mock {
            on { memberProvidedContext } doReturn memberContext
            on { mgmProvidedContext } doReturn mgmContext
            on { groupId } doReturn knownGroupId
        }
        val groupParametersCache = GroupParametersCache(
            platformInfoProvider,
            publisher,
            keyEncodingService,
            cordaAvroSerializationFactory,
            clock
        )
//        whenever(serializer.serialize(paramCaptor.capture())).doAnswer {
//            println("test")
//            serializedParams
//        }

        groupParametersCache.getOrCreateGroupParameters(knownIdentity)

        groupParametersCache.addNotary(notary)

        with(paramCaptor.secondValue) {
            assertThat(items).containsExactlyInAnyOrderElementsOf(
                listOf(
                    KeyValuePair(EPOCH_KEY, "2"),
                    KeyValuePair(MPV_KEY, MPV.toString()),
                    KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString()),
                    KeyValuePair("corda.notary.service.0.name", KNOWN_NOTARY_SERVICE),
                    KeyValuePair("corda.notary.service.0.plugin", KNOWN_NOTARY_PLUGIN),
                    KeyValuePair("corda.notary.service.0.keys.0", "test-key")
                )
            )
        }
        with(publishCaptor.firstValue.first()) {
            assertThat(key).isEqualTo(knownGroupId)
            assertThat(value).isInstanceOf(StaticGroupDefinition::class.java)

            with(value as StaticGroupDefinition) {
                assertThat(groupParameters.groupParameters).isEqualTo(ByteBuffer.wrap(serializedParams))
            }
        }
    }

    @Test
    fun `addNotary called with new keys adds keys to existing notary service`() {
        val knownKey = mock<MemberNotaryKey> {
            on { publicKey } doReturn mock()
        }
        val notaryDetails = mock<MemberNotaryDetails> {
            on { keys } doReturn listOf(knownKey)
            on { serviceName } doReturn MemberX500Name.parse(KNOWN_NOTARY_SERVICE)
            on { servicePlugin } doReturn KNOWN_NOTARY_PLUGIN
        }
        val memberContext: MemberContext = mock {
            on { entries } doReturn mapOf("${ROLES_PREFIX}.0" to NOTARY_ROLE).entries
            on { parse(eq("corda.notary"), eq(MemberNotaryDetails::class.java)) } doReturn notaryDetails
        }
        val mgmContext: MGMContext = mock {
            on { entries } doReturn mapOf("a" to "b").entries
        }
        val notary: MemberInfo = mock {
            on { memberProvidedContext } doReturn memberContext
            on { mgmProvidedContext } doReturn mgmContext
            on { groupId } doReturn knownGroupId
        }
        val groupParametersCache = GroupParametersCache(
            platformInfoProvider,
            publisher,
            keyEncodingService,
            cordaAvroSerializationFactory,
            clock
        )
        val existingGroupParameters = KeyValuePairList(
            mutableListOf(
                KeyValuePair(EPOCH_KEY, EPOCH.toString()),
                KeyValuePair(MPV_KEY, MPV.toString()),
                KeyValuePair("corda.notary.service.5.name", KNOWN_NOTARY_SERVICE),
                KeyValuePair("corda.notary.service.5.plugin", KNOWN_NOTARY_PLUGIN),
                KeyValuePair("corda.notary.service.5.keys.0", "existing-test-key"),
            )
        )
        whenever(deserializer.deserialize(serializedParams)).doReturn(existingGroupParameters)

        groupParametersCache.set(knownGroupId, signedGroupParameters)

        groupParametersCache.addNotary(notary)

        with(paramCaptor.firstValue) {
            assertThat(items).containsExactlyInAnyOrderElementsOf(
                listOf(
                    KeyValuePair(EPOCH_KEY, "2"),
                    KeyValuePair(MPV_KEY, MPV.toString()),
                    KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString()),
                    KeyValuePair("corda.notary.service.5.name", KNOWN_NOTARY_SERVICE),
                    KeyValuePair("corda.notary.service.5.plugin", KNOWN_NOTARY_PLUGIN),
                    KeyValuePair("corda.notary.service.5.keys.0", "existing-test-key"),
                    KeyValuePair("corda.notary.service.5.keys.1", "test-key")
                )
            )
        }
        with(publishCaptor.firstValue.first()) {
            assertThat(key).isEqualTo(knownGroupId)
            assertThat(value).isInstanceOf(StaticGroupDefinition::class.java)

            with(value as StaticGroupDefinition) {
                assertThat(groupParameters.groupParameters).isEqualTo(ByteBuffer.wrap(serializedParams))
            }
        }
    }
}