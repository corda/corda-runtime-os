package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.command.registration.mgm.StartRegistration
import net.corda.data.membership.command.registration.mgm.VerifyMember
import net.corda.data.membership.p2p.MembershipRegistrationRequest
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.lib.MemberInfoExtension.Companion.ENDPOINTS
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.lib.MemberInfoExtension.Companion.endpoints
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.EndpointInfo
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant
import java.util.*

class StartRegistrationHandlerTest {

    private companion object {
        val clock = TestClock(Instant.ofEpochSecond(0))
        val registrationId = UUID.randomUUID().toString()
        val x500Name = MemberX500Name.parse("O=Tester,L=London,C=GB")
        val groupId = UUID.randomUUID().toString()
        val holdingIdentity = HoldingIdentity(x500Name.toString(), groupId)

        val mgmX500Name = MemberX500Name.parse("O=TestMGM,L=London,C=GB")
        val mgmHoldingIdentity = HoldingIdentity(mgmX500Name.toString(), groupId)

        const val testTopic = "topic"
        const val testTopicKey = "key"

        val memberContext = KeyValuePairList(
            listOf(
                KeyValuePair("key", "value"),
                KeyValuePair("dummy", "test"),
                KeyValuePair("apple", "pear"),
            )
        )

        val startRegistrationCommand = getStartRegistrationCommand(holdingIdentity, memberContext)

        fun getStartRegistrationCommand(holdingIdentity: HoldingIdentity, memberContext: KeyValuePairList) =
            RegistrationCommand(
                StartRegistration(
                    mgmHoldingIdentity,
                    holdingIdentity,
                    MembershipRegistrationRequest(
                        registrationId,
                        memberContext.toByteBuffer(),
                        CryptoSignatureWithKey(
                            ByteBuffer.wrap("456".toByteArray()),
                            ByteBuffer.wrap("789".toByteArray()),
                            KeyValuePairList(emptyList())
                        )
                    )
                )
            )
    }

    // Class under test
    private lateinit var handler: StartRegistrationHandler

    // test dependencies
    lateinit var memberInfoFactory: MemberInfoFactory
    lateinit var membershipGroupReader: MembershipGroupReader
    lateinit var membershipGroupReaderProvider: MembershipGroupReaderProvider
    lateinit var deserializer: CordaAvroDeserializer<KeyValuePairList>
    lateinit var cordaAvroSerializationFactory: CordaAvroSerializationFactory
    lateinit var membershipPersistenceClient: MembershipPersistenceClient
    lateinit var membershipQueryClient: MembershipQueryClient

    val memberMemberContext: MemberContext = mock {
        on { parse(eq(GROUP_ID), eq(String::class.java)) } doReturn groupId
        on { parseList(eq(ENDPOINTS), eq(EndpointInfo::class.java)) } doReturn listOf(mock())
    }
    val memberMgmContext: MGMContext = mock {
        on { entries } doReturn emptySet()
    }
    val memberInfo: MemberInfo = mock {
        on { name } doReturn x500Name
        on { memberProvidedContext } doReturn memberMemberContext
        on { mgmProvidedContext } doReturn memberMgmContext
    }

    val mgmMemberContext: MemberContext = mock {
        on { parse(eq(GROUP_ID), eq(String::class.java)) } doReturn groupId
    }
    val mgmContext: MGMContext = mock {
        on { parseOrNull(eq(IS_MGM), any<Class<Boolean>>()) } doReturn true
    }
    val mgmMemberInfo: MemberInfo = mock {
        on { name } doReturn mgmX500Name
        on { memberProvidedContext } doReturn mgmMemberContext
        on { mgmProvidedContext } doReturn mgmContext
    }

    @BeforeEach
    fun setUp() {
        memberInfoFactory = mock {
            on { create(any<SortedMap<String, String?>>(), any()) } doReturn memberInfo
        }
        membershipGroupReader = mock {
            on { lookup(eq(mgmX500Name)) } doReturn mgmMemberInfo
        }
        membershipGroupReaderProvider = mock {
            on { getGroupReader(eq(mgmHoldingIdentity.toCorda())) } doReturn membershipGroupReader
        }
        deserializer = mock {
            on { deserialize(eq(memberContext.toByteBuffer().array())) } doReturn memberContext
        }
        cordaAvroSerializationFactory = mock {
            on { createAvroDeserializer(any(), eq(KeyValuePairList::class.java)) } doReturn deserializer
        }
        membershipPersistenceClient = mock {
            on { persistRegistrationRequest(any(), any()) } doReturn MembershipPersistenceResult.success()
            on { persistMemberInfo(any(), any()) } doReturn MembershipPersistenceResult.success()
        }
        membershipQueryClient = mock {
            on {
                queryMemberInfo(
                    eq(mgmHoldingIdentity.toCorda()),
                    any()
                )
            } doReturn MembershipQueryResult.Success<Collection<MemberInfo>>(emptyList())
        }

        handler = StartRegistrationHandler(
            clock,
            memberInfoFactory,
            membershipGroupReaderProvider,
            membershipPersistenceClient,
            membershipQueryClient,
            cordaAvroSerializationFactory
        )
    }

    @Test
    fun `invoke returns follow on records and an unchanged state`() {
        with(handler.invoke(null, Record(testTopic, testTopicKey, startRegistrationCommand))) {
            assertThat(updatedState).isNotNull
            assertThat(updatedState!!.registrationId).isEqualTo(registrationId)
            assertThat(updatedState!!.registeringMember).isEqualTo(holdingIdentity)
            assertThat(outputStates).isNotEmpty.hasSize(2)

            assertRegistrationStarted()

            val registrationCommand = this.outputStates.first().value as RegistrationCommand
            assertThat(registrationCommand.command).isInstanceOf(VerifyMember::class.java)

            val pendingMemberRecord = this.outputStates[1].value as? PersistentMemberInfo
            assertThat(pendingMemberRecord).isNotNull
            assertThat(pendingMemberRecord!!.viewOwningMember).isEqualTo(mgmHoldingIdentity)
        }
        verifyServices(
            persistRegistrationRequest = true,
            getGroupReader = true,
            lookup = true,
            createAvroDeserializer = true,
            deserialize = true,
            queryMemberInfo = true,
            persistMemberInfo = true
        )
    }

    @Test
    fun `declined if persistence of registration request fails`() {
        whenever(membershipPersistenceClient.persistRegistrationRequest(any(), any()))
            .doReturn(MembershipPersistenceResult.Failure("error"))

        with(handler.invoke(null, Record(testTopic, testTopicKey, startRegistrationCommand))) {
            assertThat(updatedState).isNotNull
            assertThat(updatedState!!.registrationId).isEqualTo(registrationId)
            assertThat(updatedState!!.registeringMember).isEqualTo(holdingIdentity)
            assertThat(outputStates).isNotEmpty.hasSize(1)

            assertDeclinedRegistration()
        }
        verifyServices(
            persistRegistrationRequest = true,
            createAvroDeserializer = true
        )
    }

    @Test
    fun `declined if target MGM info cannot be found`() {
        whenever(membershipGroupReader.lookup(eq(mgmX500Name))) doReturn null

        with(handler.invoke(null, Record(testTopic, testTopicKey, startRegistrationCommand))) {
            assertThat(updatedState).isNotNull
            assertThat(updatedState!!.registrationId).isEqualTo(registrationId)
            assertThat(updatedState!!.registeringMember).isEqualTo(holdingIdentity)
            assertThat(outputStates).isNotEmpty.hasSize(1)

            assertDeclinedRegistration()
        }
        verifyServices(
            persistRegistrationRequest = true,
            getGroupReader = true,
            lookup = true,
            createAvroDeserializer = true,
        )
    }

    @Test
    fun `declined if target MGM is not an mgm`() {
        val newMgmContext: MGMContext = mock {
            on { parseOrNull(eq(IS_MGM), any<Class<Boolean>>()) } doReturn false
        }
        whenever(mgmMemberInfo.mgmProvidedContext).thenReturn(newMgmContext)

        with(handler.invoke(null, Record(testTopic, testTopicKey, startRegistrationCommand))) {
            assertThat(updatedState).isNotNull
            assertThat(updatedState!!.registrationId).isEqualTo(registrationId)
            assertThat(updatedState!!.registeringMember).isEqualTo(holdingIdentity)
            assertThat(outputStates).isNotEmpty.hasSize(1)

            assertDeclinedRegistration()
        }
        verifyServices(
            persistRegistrationRequest = true,
            getGroupReader = true,
            lookup = true,
            createAvroDeserializer = true,
        )
    }

    @Test
    fun `declined if member context is empty`() {
        with(
            handler.invoke(
                null,
                Record(
                    testTopic, testTopicKey, getStartRegistrationCommand(
                        holdingIdentity,
                        KeyValuePairList(
                            emptyList()
                        )
                    )
                )
            )
        ) {
            assertThat(updatedState).isNotNull
            assertThat(updatedState!!.registrationId).isEqualTo(registrationId)
            assertThat(updatedState!!.registeringMember).isEqualTo(holdingIdentity)
            assertThat(outputStates).isNotEmpty.hasSize(1)

            assertDeclinedRegistration()
        }
        verifyServices(
            persistRegistrationRequest = true,
            getGroupReader = true,
            lookup = true,
            createAvroDeserializer = true,
            deserialize = true,
        )
    }

    @Test
    fun `declined if member name in context does not match the source member`() {
        val badHoldingIdentity = HoldingIdentity(MemberX500Name.parse("O=BadName,L=London,C=GB").toString(), groupId)
        with(
            handler.invoke(
                null,
                Record(
                    testTopic, testTopicKey, getStartRegistrationCommand(
                        badHoldingIdentity,
                        KeyValuePairList(
                            emptyList()
                        )
                    )
                )
            )
        ) {
            assertThat(updatedState).isNotNull
            assertThat(updatedState!!.registrationId).isEqualTo(registrationId)
            assertThat(updatedState!!.registeringMember).isEqualTo(badHoldingIdentity)
            assertThat(outputStates).isNotEmpty.hasSize(1)

            assertDeclinedRegistration()
        }
        verifyServices(
            persistRegistrationRequest = true,
            getGroupReader = true,
            lookup = true,
            createAvroDeserializer = true,
            deserialize = true,
        )
    }

    @Test
    fun `declined if member already exists`() {
        whenever(membershipQueryClient.queryMemberInfo(eq(mgmHoldingIdentity.toCorda()), any()))
            .doReturn(MembershipQueryResult.Success<Collection<MemberInfo>>(listOf(mock())))
        with(
            handler.invoke(null, Record(testTopic, testTopicKey, startRegistrationCommand))
        ) {
            assertThat(updatedState).isNotNull
            assertThat(updatedState!!.registrationId).isEqualTo(registrationId)
            assertThat(updatedState!!.registeringMember).isEqualTo(holdingIdentity)
            assertThat(outputStates).isNotEmpty.hasSize(1)

            assertDeclinedRegistration()
        }
        verifyServices(
            persistRegistrationRequest = true,
            getGroupReader = true,
            lookup = true,
            createAvroDeserializer = true,
            deserialize = true,
            queryMemberInfo = true,
        )
    }

    @Test
    fun `declined if member info has no endpoints`() {
        whenever(memberInfo.endpoints).thenReturn(emptyList())
        with(handler.invoke(null, Record(testTopic, testTopicKey, startRegistrationCommand))) {
            assertThat(updatedState).isNotNull
            assertThat(updatedState!!.registrationId).isEqualTo(registrationId)
            assertThat(updatedState!!.registeringMember).isEqualTo(holdingIdentity)
            assertThat(outputStates).isNotEmpty.hasSize(1)

            assertDeclinedRegistration()
        }
        verifyServices(
            persistRegistrationRequest = true,
            getGroupReader = true,
            lookup = true,
            createAvroDeserializer = true,
            deserialize = true,
            queryMemberInfo = true,
        )
    }

    @Test
    fun `declined if member info fails to persist`() {
        whenever(membershipPersistenceClient.persistMemberInfo(any(), any())).thenReturn(
            MembershipPersistenceResult.Failure("error")
        )
        with(handler.invoke(null, Record(testTopic, testTopicKey, startRegistrationCommand))) {
            assertThat(updatedState).isNotNull
            assertThat(updatedState!!.registrationId).isEqualTo(registrationId)
            assertThat(updatedState!!.registeringMember).isEqualTo(holdingIdentity)
            assertThat(outputStates).isNotEmpty.hasSize(1)

            assertDeclinedRegistration()
        }
        verifyServices(
            persistRegistrationRequest = true,
            getGroupReader = true,
            lookup = true,
            createAvroDeserializer = true,
            deserialize = true,
            queryMemberInfo = true,
            persistMemberInfo = true
        )
    }

    private fun RegistrationHandlerResult.assertRegistrationStarted() =
        assertExpectedOutputStates(VerifyMember::class.java)

    private fun RegistrationHandlerResult.assertDeclinedRegistration() =
        assertExpectedOutputStates(DeclineRegistration::class.java)

    private fun RegistrationHandlerResult.assertExpectedOutputStates(expectedResultClass: Class<*>) {
        with(outputStates.first()) {
            assertThat(topic).isEqualTo(Schemas.Membership.REGISTRATION_COMMAND_TOPIC)
            assertThat(key).isEqualTo(testTopicKey)
            assertThat(value).isInstanceOf(RegistrationCommand::class.java)
            assertThat((value as RegistrationCommand).command).isInstanceOf(expectedResultClass)
        }
    }

    @Suppress("LongParameterList")
    private fun verifyServices(
        persistRegistrationRequest: Boolean = false,
        getGroupReader: Boolean = false,
        lookup: Boolean = false,
        createAvroDeserializer: Boolean = false,
        deserialize: Boolean = false,
        queryMemberInfo: Boolean = false,
        persistMemberInfo: Boolean = false
    ) {
        fun getVerificationMode(condition: Boolean) = if (condition) times(1) else never()

        verify(membershipPersistenceClient, getVerificationMode(persistRegistrationRequest))
            .persistRegistrationRequest(eq(mgmHoldingIdentity.toCorda()), any())

        verify(membershipGroupReaderProvider, getVerificationMode(getGroupReader))
            .getGroupReader(eq(mgmHoldingIdentity.toCorda()))

        verify(membershipGroupReader, getVerificationMode(lookup))
            .lookup(eq(mgmX500Name))

        verify(cordaAvroSerializationFactory, getVerificationMode(createAvroDeserializer))
            .createAvroDeserializer(any(), eq(KeyValuePairList::class.java))

        verify(deserializer, getVerificationMode(deserialize))
            .deserialize(any())

        verify(membershipQueryClient, getVerificationMode(queryMemberInfo))
            .queryMemberInfo(eq(mgmHoldingIdentity.toCorda()), any())

        verify(membershipPersistenceClient, getVerificationMode(persistMemberInfo))
            .persistMemberInfo(eq(mgmHoldingIdentity.toCorda()), any())
    }
}