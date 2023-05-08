package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.command.registration.mgm.StartRegistration
import net.corda.data.membership.command.registration.mgm.VerifyMember
import net.corda.data.membership.common.RegistrationRequestDetails
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.preauth.PreAuthToken
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.impl.registration.dynamic.handler.MemberTypeChecker
import net.corda.membership.impl.registration.dynamic.handler.MissingRegistrationStateException
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.impl.registration.dynamic.verifiers.RegistrationContextCustomFieldsVerifier
import net.corda.membership.lib.InternalGroupParameters
import net.corda.membership.lib.MemberInfoExtension.Companion.CUSTOM_KEY_PREFIX
import net.corda.membership.lib.MemberInfoExtension.Companion.ENDPOINTS
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_PENDING
import net.corda.membership.lib.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.lib.MemberInfoExtension.Companion.PRE_AUTH_TOKEN
import net.corda.membership.lib.MemberInfoExtension.Companion.ROLES_PREFIX
import net.corda.membership.lib.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.lib.MemberInfoExtension.Companion.endpoints
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.NOTARY_SERVICE_NAME_KEY
import net.corda.membership.lib.notary.MemberNotaryDetails
import net.corda.membership.lib.toMap
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.test.util.time.TestClock
import net.corda.utilities.parse
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.EndpointInfo
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant
import java.util.SortedMap
import java.util.UUID

class StartRegistrationHandlerTest {

    private companion object {
        val clock = TestClock(Instant.ofEpochSecond(0))
        val registrationId = UUID(0, 1).toString()
        val aliceX500Name = MemberX500Name.parse("O=Alice,L=London,C=GB")
        val bobX500Name = MemberX500Name.parse("O=Bob,L=London,C=GB")
        val groupId = UUID(0, 1).toString()
        val aliceHoldingIdentity = HoldingIdentity(aliceX500Name.toString(), groupId)
        val bobHoldingIdentity = HoldingIdentity(bobX500Name.toString(), groupId)
        val notaryX500Name = MemberX500Name.parse("O=Notary,L=London,C=GB")

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

        val startRegistrationCommand = RegistrationCommand(StartRegistration())

        val registrationState = getRegistrationState(registrationId, aliceHoldingIdentity, mgmHoldingIdentity)

        fun getRegistrationState(registrationId: String, member: HoldingIdentity, mgm: HoldingIdentity) =
            RegistrationState(registrationId, member, mgm)
    }

    // Class under test
    private lateinit var handler: RegistrationHandler<StartRegistration>

    // test dependencies
    private lateinit var memberInfoFactory: MemberInfoFactory
    lateinit var membershipPersistenceClient: MembershipPersistenceClient
    private lateinit var membershipQueryClient: MembershipQueryClient

    private val memberContextEntries = mapOf("$ROLES_PREFIX.0" to "notary", "$CUSTOM_KEY_PREFIX.0" to "test")
    private val memberMemberContext: MemberContext = mock {
        on { parse(eq(GROUP_ID), eq(String::class.java)) } doReturn groupId
        on { parseList(eq(ENDPOINTS), eq(EndpointInfo::class.java)) } doReturn listOf(mock())
        on { entries } doReturn memberContextEntries.entries
    }
    private val memberMgmContext: MGMContext = mock {
        on { parse(eq(MODIFIED_TIME), eq(Instant::class.java)) } doReturn clock.instant()
        on { entries } doReturn emptySet()
    }
    private val pendingMemberInfo: MemberInfo = mock {
        on { name } doReturn aliceX500Name
        on { isActive } doReturn true
        on { memberProvidedContext } doReturn memberMemberContext
        on { mgmProvidedContext } doReturn memberMgmContext
        on { status } doReturn MEMBER_STATUS_PENDING
        on { serial } doReturn 1L
    }
    private val activeMemberInfo: MemberInfo = mock {
        on { name } doReturn aliceX500Name
        on { isActive } doReturn true
        on { memberProvidedContext } doReturn memberMemberContext
        on { mgmProvidedContext } doReturn memberMgmContext
        on { status } doReturn MEMBER_STATUS_ACTIVE
        on { serial } doReturn 1L
    }

    private val mgmMemberContext: MemberContext = mock {
        on { parse(eq(GROUP_ID), eq(String::class.java)) } doReturn groupId
    }
    private val mgmContext: MGMContext = mock {
        on { parseOrNull(eq(IS_MGM), any<Class<Boolean>>()) } doReturn true
    }
    private val mgmMemberInfo: MemberInfo = mock {
        on { name } doReturn mgmX500Name
        on { memberProvidedContext } doReturn mgmMemberContext
        on { mgmProvidedContext } doReturn mgmContext
    }

    private val memberTypeChecker = mock<MemberTypeChecker> {
        on { getMgmMemberInfo(mgmHoldingIdentity.toCorda()) } doReturn mgmMemberInfo
    }
    private val mockGroupParameters = mock<InternalGroupParameters> {
        on { entries } doReturn mapOf(String.format(NOTARY_SERVICE_NAME_KEY, 0) to notaryX500Name.toString()).entries
    }
    private val groupReader = mock<MembershipGroupReader> {
        on { groupParameters } doReturn mockGroupParameters
    }
    private val membershipGroupReaderProvider = mock<MembershipGroupReaderProvider> {
        on { getGroupReader(mgmHoldingIdentity.toCorda()) } doReturn groupReader
    }
    private val registrationContextCustomFieldsVerifier = mock<RegistrationContextCustomFieldsVerifier> {
        on { verify(any()) } doReturn RegistrationContextCustomFieldsVerifier.Result.Success
    }

    private val registrationRequest = createRegistrationRequest()

    private fun createRegistrationRequest(
        registeringMember: HoldingIdentity = aliceHoldingIdentity,
        context: KeyValuePairList = memberContext,
        serial: Long? = 0L
    ) = RegistrationRequestDetails(
        clock.instant(),
        clock.instant(),
        RegistrationStatus.RECEIVED_BY_MGM,
        registrationId,
        registeringMember.toCorda().shortHash.value,
        1,
        context,
        CryptoSignatureWithKey(
            ByteBuffer.wrap("456".toByteArray()),
            ByteBuffer.wrap("789".toByteArray())
        ),
        CryptoSignatureSpec("", null, null),
        "",
        serial,
    )

    @BeforeEach
    fun setUp() {
        memberInfoFactory = mock {
            on { create(any<SortedMap<String, String?>>(), any()) } doReturn pendingMemberInfo
        }
        membershipPersistenceClient = mock {
            on {
                setRegistrationRequestStatus(any(), any(), eq(RegistrationStatus.STARTED_PROCESSING_BY_MGM), anyOrNull())
            } doReturn MembershipPersistenceResult.success()
            on { persistMemberInfo(any(), any()) } doReturn MembershipPersistenceResult.success()
        }
        membershipQueryClient = mock {
            on {
                queryMemberInfo(
                    eq(mgmHoldingIdentity.toCorda()),
                    any()
                )
            } doReturn MembershipQueryResult.Success(emptyList())
            on {
                queryRegistrationRequest(eq(mgmHoldingIdentity.toCorda()), eq(registrationId))
            } doReturn MembershipQueryResult.Success(registrationRequest)
        }

        handler = StartRegistrationHandler(
            clock,
            memberInfoFactory,
            memberTypeChecker,
            membershipPersistenceClient,
            membershipQueryClient,
            membershipGroupReaderProvider,
            registrationContextCustomFieldsVerifier
        )
    }

    @Test
    fun `invoke returns follow on records and an unchanged state`() {
        with(handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))) {
            assertThat(updatedState).isNotNull
            assertThat(updatedState!!.registrationId).isEqualTo(registrationId)
            assertThat(updatedState!!.registeringMember).isEqualTo(aliceHoldingIdentity)
            assertThat(outputStates).isNotEmpty.hasSize(2)

            assertRegistrationStarted()

            val registrationCommand = this.outputStates.first().value as RegistrationCommand
            assertThat(registrationCommand.command).isInstanceOf(VerifyMember::class.java)

            val pendingMemberRecord = this.outputStates[1].value as? PersistentMemberInfo
            assertThat(pendingMemberRecord).isNotNull
            assertThat(pendingMemberRecord!!.viewOwningMember).isEqualTo(mgmHoldingIdentity)
        }
        verifyServices(
            updateRegistrationRequest = true,
            verify = true,
            verifyCustomFields = true,
            queryMemberInfo = true,
            persistMemberInfo = true,
            queryRegistrationRequest = true,
        )
    }

    @Test
    fun `serial is increased when building pending member info`() {
        whenever(membershipQueryClient.queryRegistrationRequest(eq(mgmHoldingIdentity.toCorda()), eq(registrationId)))
            .thenReturn(MembershipQueryResult.Success(createRegistrationRequest(serial = 10L)))
        val mgmContextCaptor = argumentCaptor<SortedMap<String, String?>>()
        handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))
        verify(memberInfoFactory).create(any(), mgmContextCaptor.capture())
        assertThat(mgmContextCaptor.firstValue[SERIAL]).isEqualTo("11")
    }

    @Test
    fun `exception is thrown when registration state is null`() {
        assertThrows<MissingRegistrationStateException> {
            handler.invoke(null, Record(testTopic, testTopicKey, startRegistrationCommand))
        }
    }

    @Test
    fun `declined if serial in the registration request is null`() {
        whenever(membershipQueryClient.queryRegistrationRequest(eq(mgmHoldingIdentity.toCorda()), eq(registrationId)))
            .thenReturn(MembershipQueryResult.Success(createRegistrationRequest(serial = null)))
        with(handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))) {
            assertThat(updatedState).isNotNull
            assertThat(updatedState!!.registrationId).isEqualTo(registrationId)
            assertThat(updatedState!!.registeringMember).isEqualTo(aliceHoldingIdentity)
            assertThat(outputStates).isNotEmpty.hasSize(1)

            assertDeclinedRegistration()
        }
    }

    @Test
    fun `declined if updating the status of the registration request fails`() {
        whenever(membershipPersistenceClient.setRegistrationRequestStatus(any(), any(), any(), anyOrNull()))
            .doReturn(MembershipPersistenceResult.Failure("error"))

        with(handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))) {
            assertThat(updatedState).isNotNull
            assertThat(updatedState!!.registrationId).isEqualTo(registrationId)
            assertThat(updatedState!!.registeringMember).isEqualTo(aliceHoldingIdentity)
            assertThat(outputStates).isNotEmpty.hasSize(1)

            assertDeclinedRegistration()
        }
        verifyServices(
            updateRegistrationRequest = true,
            verify = true,
            queryRegistrationRequest = true,
        )
    }

    @Test
    fun `declined if target MGM is not an mgm`() {
        whenever(memberTypeChecker.getMgmMemberInfo(mgmHoldingIdentity.toCorda())).doReturn(null)

        with(handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))) {
            assertThat(updatedState).isNotNull
            assertThat(updatedState!!.registrationId).isEqualTo(registrationId)
            assertThat(updatedState!!.registeringMember).isEqualTo(aliceHoldingIdentity)
            assertThat(outputStates).isNotEmpty.hasSize(1)

            assertDeclinedRegistration()
        }
        verifyServices(
            verify = true,
        )
    }

    @Test
    fun `declined if target member is an mgm`() {
        whenever(memberTypeChecker.isMgm(aliceHoldingIdentity.toCorda())).doReturn(true)

        with(handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))) {
            assertThat(updatedState).isNotNull
            assertThat(updatedState!!.registrationId).isEqualTo(registrationId)
            assertThat(updatedState!!.registeringMember).isEqualTo(aliceHoldingIdentity)
            assertThat(outputStates).isNotEmpty.hasSize(1)

            assertDeclinedRegistration()
        }
        verifyServices(
            verify = true,
            queryRegistrationRequest = true,
        )
    }

    @Test
    fun `declined if member context is empty`() {
        whenever(membershipQueryClient.queryRegistrationRequest(eq(mgmHoldingIdentity.toCorda()), eq(registrationId)))
            .thenReturn(MembershipQueryResult.Success(createRegistrationRequest(context = KeyValuePairList(emptyList()))))
        with(handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))) {
            assertThat(updatedState).isNotNull
            assertThat(updatedState!!.registrationId).isEqualTo(registrationId)
            assertThat(updatedState!!.registeringMember).isEqualTo(aliceHoldingIdentity)
            assertThat(outputStates).isNotEmpty.hasSize(1)

            assertDeclinedRegistration()
        }
        verifyServices(
            updateRegistrationRequest = true,
            verify = true,
            queryRegistrationRequest = true,
        )
    }


    @Test
    fun `declined if customs fields in member fail validation`() {
        whenever(registrationContextCustomFieldsVerifier.verify(any()))
            .thenReturn(RegistrationContextCustomFieldsVerifier.Result.Failure(""))
        with(handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))) {
            assertThat(updatedState).isNotNull
            assertThat(updatedState!!.registrationId).isEqualTo(registrationId)
            assertThat(updatedState!!.registeringMember).isEqualTo(aliceHoldingIdentity)
            assertThat(outputStates).isNotEmpty.hasSize(1)

            assertDeclinedRegistration()
        }
        verifyServices(
            updateRegistrationRequest = true,
            verify = true,
            verifyCustomFields = true,
            queryRegistrationRequest = true,
        )
    }

    @Test
    fun `declined if member name in context does not match the source member`() {
        val badHoldingIdentity = HoldingIdentity(MemberX500Name.parse("O=BadName,L=London,C=GB").toString(), groupId)
        with(
            handler.invoke(
                RegistrationState(registrationId, badHoldingIdentity, mgmHoldingIdentity),
                Record(testTopic, testTopicKey, startRegistrationCommand)
            )
        ) {
            assertThat(updatedState).isNotNull
            assertThat(updatedState!!.registrationId).isEqualTo(registrationId)
            assertThat(updatedState!!.registeringMember).isEqualTo(badHoldingIdentity)
            assertThat(outputStates).isNotEmpty.hasSize(1)

            assertDeclinedRegistration()
        }
        verifyServices(
            updateRegistrationRequest = true,
            verify = true,
            verifyCustomFields = true,
            queryRegistrationRequest = true,
        )
    }

    @Test
    fun `declined if member already exists`() {
        whenever(membershipQueryClient.queryMemberInfo(eq(mgmHoldingIdentity.toCorda()), any()))
            .doReturn(MembershipQueryResult.Success(listOf(activeMemberInfo)))
        with(
            handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))
        ) {
            assertThat(updatedState).isNotNull
            assertThat(updatedState!!.registrationId).isEqualTo(registrationId)
            assertThat(updatedState!!.registeringMember).isEqualTo(aliceHoldingIdentity)
            assertThat(outputStates).isNotEmpty.hasSize(1)

            assertDeclinedRegistration()
        }
        verifyServices(
            updateRegistrationRequest = true,
            verify = true,
            verifyCustomFields = true,
            queryMemberInfo = true,
            queryRegistrationRequest = true,
        )
    }

    @Test
    fun `declined if member's current serial is larger than the serial in the request`() {
        val activeMemberInfo: MemberInfo = mock {
            on { name } doReturn aliceX500Name
            on { isActive } doReturn true
            on { memberProvidedContext } doReturn memberMemberContext
            on { mgmProvidedContext } doReturn memberMgmContext
            on { status } doReturn MEMBER_STATUS_ACTIVE
            on { serial } doReturn 2L
        }
        whenever(membershipQueryClient.queryMemberInfo(eq(mgmHoldingIdentity.toCorda()), any()))
            .doReturn(MembershipQueryResult.Success(listOf(activeMemberInfo)))
        whenever(membershipQueryClient.queryRegistrationRequest(eq(mgmHoldingIdentity.toCorda()), eq(registrationId)))
            .thenReturn(MembershipQueryResult.Success(createRegistrationRequest(serial = 1L)))
        with(handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))) {
            assertThat(updatedState).isNotNull
            assertThat(updatedState!!.registrationId).isEqualTo(registrationId)
            assertThat(updatedState!!.registeringMember).isEqualTo(aliceHoldingIdentity)
            assertThat(outputStates).isNotEmpty.hasSize(1)

            assertDeclinedRegistration()
        }
    }

    @Test
    fun `declined if member info has no endpoints`() {
        whenever(pendingMemberInfo.endpoints).thenReturn(emptyList())
        with(handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))) {
            assertThat(updatedState).isNotNull
            assertThat(updatedState!!.registrationId).isEqualTo(registrationId)
            assertThat(updatedState!!.registeringMember).isEqualTo(aliceHoldingIdentity)
            assertThat(outputStates).isNotEmpty.hasSize(1)

            assertDeclinedRegistration()
        }
        verifyServices(
            updateRegistrationRequest = true,
            verify = true,
            verifyCustomFields = true,
            queryMemberInfo = true,
            queryRegistrationRequest = true,
        )
    }

    @Test
    fun `declined if member info fails to persist`() {
        whenever(membershipPersistenceClient.persistMemberInfo(any(), any())).thenReturn(
            MembershipPersistenceResult.Failure("error")
        )
        with(handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))) {
            assertThat(updatedState).isNotNull
            assertThat(updatedState!!.registrationId).isEqualTo(registrationId)
            assertThat(updatedState!!.registeringMember).isEqualTo(aliceHoldingIdentity)
            assertThat(outputStates).isNotEmpty.hasSize(1)

            assertDeclinedRegistration()
        }
        verifyServices(
            updateRegistrationRequest = true,
            verify = true,
            verifyCustomFields = true,
            queryMemberInfo = true,
            persistMemberInfo = true,
            queryRegistrationRequest = true,
        )
    }

    @Test
    fun `invoke returns follow on records when role is set to notary`() {
        val notaryServiceName = MemberX500Name.parse("O=NotaryService,L=London,C=GB")
        val notaryDetails = MemberNotaryDetails(
            notaryServiceName,
            "Notary Plugin A",
            listOf(1),
            listOf(mock())
        )
        whenever(memberMemberContext.parse<MemberNotaryDetails>("corda.notary")).thenReturn(notaryDetails)
        whenever(
            membershipQueryClient.queryMemberInfo(
                mgmHoldingIdentity.toCorda(),
                listOf(HoldingIdentity(notaryServiceName.toString(), groupId).toCorda())
            )
        ).thenReturn(MembershipQueryResult.Success(emptyList()))

        val result = handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))

        result.assertRegistrationStarted()
    }

    @Test
    fun `declined if role is set to notary but notary keys are missing`() {
        val notaryDetails = MemberNotaryDetails(
            aliceX500Name,
            null,
            emptyList(),
            emptyList()
        )
        whenever(memberMemberContext.parse<MemberNotaryDetails>("corda.notary")).thenReturn(notaryDetails)

        val result = handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))

        result.assertDeclinedRegistration()
    }

    @Test
    fun `declined if role is set to notary and notary service plugin type is specified but blank`() {
        val notaryDetails = MemberNotaryDetails(
            aliceX500Name,
            " ",
            listOf(1),
            listOf(mock())
        )
        whenever(memberMemberContext.parse<MemberNotaryDetails>("corda.notary")).thenReturn(notaryDetails)

        val result = handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))

        result.assertDeclinedRegistration()
    }

    @Test
    fun `declined if notary service name is the same as the virtual node name`() {
        val notaryDetails = MemberNotaryDetails(
            aliceX500Name,
            "pluginType",
            listOf(1),
            listOf(mock())
        )
        whenever(memberMemberContext.parse<MemberNotaryDetails>("corda.notary")).thenReturn(notaryDetails)

        val result = handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))

        result.assertDeclinedRegistration()
    }

    @Test
    fun `declined if registering member's name is the same as an existing notary's service name`() {
        val notaryDetails = MemberNotaryDetails(
            notaryX500Name,
            "Notary Protocol A",
            listOf(1),
            listOf(mock())
        )
        whenever(memberMemberContext.parse<MemberNotaryDetails>("corda.notary")).thenReturn(notaryDetails)

        val notaryResult = handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))
        notaryResult.assertRegistrationStarted()

        val memberStartRegistrationCommand = startRegistrationCommand
        val memberResult = handler.invoke(
            RegistrationState(registrationId, HoldingIdentity(notaryX500Name.toString(), groupId), mgmHoldingIdentity),
            Record(testTopic, testTopicKey, memberStartRegistrationCommand)
        )
        memberResult.assertDeclinedRegistration()
    }

    @Test
    fun `declined if role is set to notary and notary service name already exists`() {
        val notaryDetails = MemberNotaryDetails(
            notaryX500Name,
            "Notary Protocol A",
            listOf(1),
            listOf(mock())
        )
        whenever(memberMemberContext.parse<MemberNotaryDetails>("corda.notary")).thenReturn(notaryDetails)
        val existingNotaryContext = mock<MemberContext> {
            on { entries } doReturn memberContextEntries.entries
        }
        whenever(existingNotaryContext.parse<MemberNotaryDetails>("corda.notary")).thenReturn(notaryDetails)
        val existingNotary = mock<MemberInfo> {
            on { name } doReturn bobX500Name
            on { memberProvidedContext } doReturn existingNotaryContext
            on { mgmProvidedContext } doReturn mock()
        }
        whenever(groupReader.lookup()).thenReturn(setOf(existingNotary))

        val result = handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))

        result.assertDeclinedRegistration()
    }

    @Test
    fun `declined if role is set to notary and group parameters cannot be read`() {
        val notaryDetails = MemberNotaryDetails(
            notaryX500Name,
            "Notary Plugin A",
            listOf(1),
            listOf(mock())
        )
        whenever(memberMemberContext.parse<MemberNotaryDetails>("corda.notary")).thenReturn(notaryDetails)
        whenever(groupReader.groupParameters).thenReturn(null)

        val result = handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))
        result.assertDeclinedRegistration()
    }

    @Test
    fun `declined if notary service name is the same as an existing member's virtual node name`() {
        val notaryDetails = MemberNotaryDetails(
            aliceX500Name,
            "pluginType",
            listOf(1),
            listOf(mock())
        )
        val bobInfo: MemberInfo = mock {
            on { name } doReturn bobX500Name
            on { isActive } doReturn true
            on { memberProvidedContext } doReturn memberMemberContext
            on { mgmProvidedContext } doReturn memberMgmContext
        }
        whenever(memberMemberContext.parse<MemberNotaryDetails>("corda.notary")).thenReturn(notaryDetails)
        whenever(memberInfoFactory.create(any<SortedMap<String, String?>>(), any())).thenReturn(bobInfo)
        whenever(
            membershipQueryClient.queryMemberInfo(
                mgmHoldingIdentity.toCorda(),
                listOf(HoldingIdentity(aliceX500Name.toString(), groupId).toCorda())
            )
        ).thenReturn(MembershipQueryResult.Success(listOf(pendingMemberInfo)))

        val registrationCommand = startRegistrationCommand
        val result = handler.invoke(RegistrationState(
            registrationId, bobHoldingIdentity, mgmHoldingIdentity),
            Record(testTopic, testTopicKey, registrationCommand)
        )
        result.assertDeclinedRegistration()
    }

    @Test
    fun `declined if persistence failure happened when trying to query for existing member info`() {
        whenever(
            membershipQueryClient.queryMemberInfo(
                mgmHoldingIdentity.toCorda(),
                listOf(HoldingIdentity(aliceX500Name.toString(), groupId).toCorda())
            )
        ).thenReturn(MembershipQueryResult.Failure("error happened"))

        with(handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))) {
            assertThat(updatedState).isNotNull
            assertThat(updatedState!!.registrationId).isEqualTo(registrationId)
            assertThat(updatedState!!.registeringMember).isEqualTo(aliceHoldingIdentity)
            assertThat(outputStates).isNotEmpty.hasSize(1)

            assertDeclinedRegistration()
        }
    }

    @Test
    fun `declined if non-custom properties are added during re-registration`() {
        val contextWithUpdates = mock<MemberContext> {
            on { parse(eq(GROUP_ID), eq(String::class.java)) } doReturn groupId
            on { parseList(eq(ENDPOINTS), eq(EndpointInfo::class.java)) } doReturn listOf(mock())
            on { entries } doReturn (memberContextEntries + mapOf("$ROLES_PREFIX.1" to "added")).entries
        }
        whenever(pendingMemberInfo.memberProvidedContext).doReturn(contextWithUpdates)
        whenever(membershipQueryClient.queryMemberInfo(eq(mgmHoldingIdentity.toCorda()), any()))
            .doReturn(MembershipQueryResult.Success(listOf(activeMemberInfo)))
        whenever(membershipQueryClient.queryRegistrationRequest(eq(mgmHoldingIdentity.toCorda()), eq(registrationId)))
            .doReturn(MembershipQueryResult.Success(createRegistrationRequest(serial = 2L)))
        val result = handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))
        result.assertDeclinedRegistration()
    }

    @Test
    fun `declined if non-custom properties are removed during re-registration`() {
        val contextWithUpdates = mock<MemberContext> {
            on { parse(eq(GROUP_ID), eq(String::class.java)) } doReturn groupId
            on { parseList(eq(ENDPOINTS), eq(EndpointInfo::class.java)) } doReturn listOf(mock())
            on { entries } doReturn emptySet()
        }
        whenever(pendingMemberInfo.memberProvidedContext).doReturn(contextWithUpdates)
        whenever(membershipQueryClient.queryMemberInfo(eq(mgmHoldingIdentity.toCorda()), any()))
            .doReturn(MembershipQueryResult.Success(listOf(activeMemberInfo)))
        whenever(membershipQueryClient.queryRegistrationRequest(eq(mgmHoldingIdentity.toCorda()), eq(registrationId)))
            .doReturn(MembershipQueryResult.Success(createRegistrationRequest(serial = 2L)))
        val result = handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))
        result.assertDeclinedRegistration()
    }

    @Test
    fun `declined if non-custom properties are updated during re-registration`() {
        val newContextEntries = memberContextEntries.toMutableMap().apply {
            put("${ROLES_PREFIX}0", "changed")
        }.entries
        val contextWithUpdates = mock<MemberContext> {
            on { parse(eq(GROUP_ID), eq(String::class.java)) } doReturn groupId
            on { parseList(eq(ENDPOINTS), eq(EndpointInfo::class.java)) } doReturn listOf(mock())
            on { entries } doReturn newContextEntries
        }
        whenever(pendingMemberInfo.memberProvidedContext).doReturn(contextWithUpdates)
        whenever(membershipQueryClient.queryMemberInfo(eq(mgmHoldingIdentity.toCorda()), any()))
            .doReturn(MembershipQueryResult.Success(listOf(activeMemberInfo)))
        whenever(membershipQueryClient.queryRegistrationRequest(eq(mgmHoldingIdentity.toCorda()), eq(registrationId)))
            .doReturn(MembershipQueryResult.Success(createRegistrationRequest(serial = 2L)))
        val result = handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))
        result.assertDeclinedRegistration()
    }

    @Test
    fun `allows custom properties to be added during re-registration`() {
        val contextWithUpdates = mock<MemberContext> {
            on { parse(eq(GROUP_ID), eq(String::class.java)) } doReturn groupId
            on { parseList(eq(ENDPOINTS), eq(EndpointInfo::class.java)) } doReturn listOf(mock())
            on { entries } doReturn (memberContextEntries + mapOf("$CUSTOM_KEY_PREFIX.1" to "added")).entries
        }
        whenever(pendingMemberInfo.memberProvidedContext).doReturn(contextWithUpdates)
        whenever(membershipQueryClient.queryMemberInfo(eq(mgmHoldingIdentity.toCorda()), any()))
            .doReturn(MembershipQueryResult.Success(listOf(activeMemberInfo)))
        whenever(membershipQueryClient.queryRegistrationRequest(eq(mgmHoldingIdentity.toCorda()), eq(registrationId)))
            .doReturn(MembershipQueryResult.Success(createRegistrationRequest(serial = 2L)))
        val result = handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))
        result.assertRegistrationStarted()
    }

    @Test
    fun `allows custom properties to be removed during re-registration`() {
        val contextWithUpdates = mock<MemberContext> {
            on { parse(eq(GROUP_ID), eq(String::class.java)) } doReturn groupId
            on { parseList(eq(ENDPOINTS), eq(EndpointInfo::class.java)) } doReturn listOf(mock())
            on { entries } doReturn memberContextEntries.filterNot { it.key == "$CUSTOM_KEY_PREFIX.0" }.entries
        }
        whenever(pendingMemberInfo.memberProvidedContext).doReturn(contextWithUpdates)
        whenever(membershipQueryClient.queryMemberInfo(eq(mgmHoldingIdentity.toCorda()), any()))
            .doReturn(MembershipQueryResult.Success(listOf(activeMemberInfo)))
        whenever(membershipQueryClient.queryRegistrationRequest(eq(mgmHoldingIdentity.toCorda()), eq(registrationId)))
            .doReturn(MembershipQueryResult.Success(createRegistrationRequest(serial = 2L)))
        val result = handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))
        result.assertRegistrationStarted()
    }

    @Test
    fun `allows custom properties to be updated during re-registration`() {
        val newContextEntries = memberContextEntries.toMutableMap().apply {
            put("$CUSTOM_KEY_PREFIX.0", "changed")
        }.entries
        val contextWithUpdates = mock<MemberContext> {
            on { parse(eq(GROUP_ID), eq(String::class.java)) } doReturn groupId
            on { parseList(eq(ENDPOINTS), eq(EndpointInfo::class.java)) } doReturn listOf(mock())
            on { entries } doReturn newContextEntries
        }
        whenever(pendingMemberInfo.memberProvidedContext).doReturn(contextWithUpdates)
        whenever(membershipQueryClient.queryMemberInfo(eq(mgmHoldingIdentity.toCorda()), any()))
            .doReturn(MembershipQueryResult.Success(listOf(activeMemberInfo)))
        whenever(membershipQueryClient.queryRegistrationRequest(eq(mgmHoldingIdentity.toCorda()), eq(registrationId)))
            .doReturn(MembershipQueryResult.Success(createRegistrationRequest(serial = 2L)))
        val result = handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))
        result.assertRegistrationStarted()
    }

    @Test
    fun `allows notary virtual node re-registration`() {
        val notaryDetails = MemberNotaryDetails(
            notaryX500Name,
            "Notary Protocol A",
            listOf(1),
            listOf(mock())
        )
        whenever(memberMemberContext.parse<MemberNotaryDetails>("corda.notary")).thenReturn(notaryDetails)
        val existingNotaryContext = mock<MemberContext> {
            on { entries } doReturn memberContextEntries.entries
        }
        whenever(existingNotaryContext.parse<MemberNotaryDetails>("corda.notary")).thenReturn(notaryDetails)
        val existingNotary = mock<MemberInfo> {
            on { name } doReturn aliceX500Name
            on { memberProvidedContext } doReturn existingNotaryContext
            on { mgmProvidedContext } doReturn mock()
        }
        whenever(groupReader.lookup()).thenReturn(setOf(existingNotary))

        val result = handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))

        result.assertRegistrationStarted()
    }

    @Nested
    inner class PreAuthTokenTest {
        @Test
        fun `Invalid UUID for preauth token results in declined registration`() {
            whenever(memberMemberContext.parseOrNull(PRE_AUTH_TOKEN, UUID::class.java)).doThrow(
                IllegalArgumentException("bad-token")
            )

            val result = handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))

            verify(memberMemberContext).parseOrNull(PRE_AUTH_TOKEN, UUID::class.java)
            verify(membershipQueryClient, never()).queryPreAuthTokens(any(), any(), any(), any())
            result.assertDeclinedRegistration()
        }

        @Test
        fun `Exception while querying for pre-auth tokens causes a declined registration`() {
            val token = UUID(0, 1)
            whenever(memberMemberContext.parseOrNull(PRE_AUTH_TOKEN, UUID::class.java)).doReturn(token)
            whenever(membershipQueryClient.queryPreAuthTokens(any(), any(), eq(token), any())).doReturn(
                MembershipQueryResult.Failure("failed-query")
            )

            val result = handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))

            verify(membershipQueryClient).queryPreAuthTokens(any(), any(), any(), any())
            result.assertDeclinedRegistration()
        }

        @Test
        fun `No matching pre-auth token results in declined registration`() {
            val token = UUID(0, 1)
            whenever(memberMemberContext.parseOrNull(PRE_AUTH_TOKEN, UUID::class.java)).doReturn(token)
            whenever(membershipQueryClient.queryPreAuthTokens(any(), any(), eq(token), any())).doReturn(
                MembershipQueryResult.Success(emptyList())
            )

            val result = handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))

            verify(membershipQueryClient).queryPreAuthTokens(any(), any(), any(), any())
            result.assertDeclinedRegistration()
        }

        @Test
        fun `Matching pre-auth token found results in started registration`() {
            val token = UUID(0, 1)
            val persistedToken: PreAuthToken = mock()
            whenever(memberMemberContext.parseOrNull(PRE_AUTH_TOKEN, UUID::class.java)).doReturn(token)
            whenever(membershipQueryClient.queryPreAuthTokens(any(), any(), eq(token), any())).doReturn(
                MembershipQueryResult.Success(listOf(persistedToken))
            )

            val result = handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))

            result.assertRegistrationStarted()
        }

        @Test
        fun `Matching pre-auth token with null TTL results in started registration`() {
            val token = UUID(0, 1)
            val persistedToken: PreAuthToken = mock {
                on { ttl } doReturn null
            }
            whenever(memberMemberContext.parseOrNull(PRE_AUTH_TOKEN, UUID::class.java)).doReturn(token)
            whenever(membershipQueryClient.queryPreAuthTokens(any(), any(), eq(token), any())).doReturn(
                MembershipQueryResult.Success(listOf(persistedToken))
            )

            val result = handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))

            result.assertRegistrationStarted()
        }

        @Test
        fun `Matching pre-auth token with not expired TTL results in started registration`() {
            val token = UUID(0, 1)
            val persistedToken: PreAuthToken = mock {
                on { ttl } doReturn clock.instant().plusSeconds(600)
            }
            whenever(memberMemberContext.parseOrNull(PRE_AUTH_TOKEN, UUID::class.java)).doReturn(token)
            whenever(membershipQueryClient.queryPreAuthTokens(any(), any(), eq(token), any())).doReturn(
                MembershipQueryResult.Success(listOf(persistedToken))
            )

            val result = handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))

            result.assertRegistrationStarted()
        }

        @Test
        fun `Matching pre-auth token with expired TTL results in declined registration`() {
            val token = UUID(0, 1)
            val persistedToken: PreAuthToken = mock {
                on { ttl } doReturn clock.instant().minusSeconds(600)
            }
            whenever(memberMemberContext.parseOrNull(PRE_AUTH_TOKEN, UUID::class.java)).doReturn(token)
            whenever(membershipQueryClient.queryPreAuthTokens(any(), any(), eq(token), any())).doReturn(
                MembershipQueryResult.Success(listOf(persistedToken))
            )

            val result = handler.invoke(registrationState, Record(testTopic, testTopicKey, startRegistrationCommand))

            result.assertDeclinedRegistration()
        }
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
        updateRegistrationRequest: Boolean = false,
        verify: Boolean = false,
        verifyCustomFields: Boolean = false,
        queryMemberInfo: Boolean = false,
        persistMemberInfo: Boolean = false,
        queryRegistrationRequest: Boolean = false,
    ) {
        fun getVerificationMode(condition: Boolean) = if (condition) times(1) else never()

        verify(membershipPersistenceClient, getVerificationMode(updateRegistrationRequest))
            .setRegistrationRequestStatus(
                eq(mgmHoldingIdentity.toCorda()), any(), eq(RegistrationStatus.STARTED_PROCESSING_BY_MGM), anyOrNull()
            )

        verify(membershipQueryClient, getVerificationMode(queryMemberInfo))
            .queryMemberInfo(eq(mgmHoldingIdentity.toCorda()), any())

        verify(membershipPersistenceClient, getVerificationMode(persistMemberInfo))
            .persistMemberInfo(eq(mgmHoldingIdentity.toCorda()), any())

        verify(registrationContextCustomFieldsVerifier, getVerificationMode(verifyCustomFields))
            .verify(memberContext.toMap())

        verify(memberTypeChecker, getVerificationMode(verify))
            .getMgmMemberInfo(eq(mgmHoldingIdentity.toCorda()))

        verify(membershipQueryClient, getVerificationMode(queryRegistrationRequest))
            .queryRegistrationRequest(eq(mgmHoldingIdentity.toCorda()), any())
    }
}