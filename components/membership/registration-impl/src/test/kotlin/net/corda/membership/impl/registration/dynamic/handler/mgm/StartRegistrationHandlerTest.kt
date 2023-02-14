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
import net.corda.data.membership.preauth.PreAuthToken
import net.corda.membership.impl.registration.dynamic.handler.MemberTypeChecker
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.lib.MemberInfoExtension.Companion.ENDPOINTS
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_PENDING
import net.corda.membership.lib.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.lib.MemberInfoExtension.Companion.PRE_AUTH_TOKEN
import net.corda.membership.lib.MemberInfoExtension.Companion.ROLES_PREFIX
import net.corda.membership.lib.MemberInfoExtension.Companion.endpoints
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.notary.MemberNotaryDetails
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.parse
import net.corda.v5.membership.EndpointInfo
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
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
        val x500Name = MemberX500Name.parse("O=Tester,L=London,C=GB")
        val groupId = UUID(0, 1).toString()
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
                        ),
                        true
                    )
                )
            )
    }

    // Class under test
    private lateinit var handler: RegistrationHandler<StartRegistration>

    // test dependencies
    private lateinit var memberInfoFactory: MemberInfoFactory
    lateinit var membershipPersistenceClient: MembershipPersistenceClient
    private lateinit var membershipQueryClient: MembershipQueryClient

    private val memberMemberContext: MemberContext = mock {
        on { parse(eq(GROUP_ID), eq(String::class.java)) } doReturn groupId
        on { parseList(eq(ENDPOINTS), eq(EndpointInfo::class.java)) } doReturn listOf(mock())
        on { entries } doReturn mapOf("$ROLES_PREFIX.0" to "notary").entries
    }
    private val memberMgmContext: MGMContext = mock {
        on { parse(eq(MODIFIED_TIME), eq(Instant::class.java)) } doReturn clock.instant()
        on { entries } doReturn emptySet()
    }
    private val memberInfo: MemberInfo = mock {
        on { name } doReturn x500Name
        on { isActive } doReturn true
        on { memberProvidedContext } doReturn memberMemberContext
        on { mgmProvidedContext } doReturn memberMgmContext
        on { status } doReturn MEMBER_STATUS_PENDING
    }
    private val declinedMember: MemberInfo = mock {
        on { isActive } doReturn false
        on { memberProvidedContext } doReturn memberMemberContext
        on { mgmProvidedContext } doReturn memberMgmContext
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

    @BeforeEach
    fun setUp() {
        val deserializer = mock<CordaAvroDeserializer<KeyValuePairList>> {
            on { deserialize(eq(memberContext.toByteBuffer().array())) } doReturn memberContext
        }
        val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
            on { createAvroDeserializer(any(), eq(KeyValuePairList::class.java)) } doReturn deserializer
        }
        memberInfoFactory = mock {
            on { create(any<SortedMap<String, String?>>(), any()) } doReturn memberInfo
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
            } doReturn MembershipQueryResult.Success(emptyList())
        }

        handler = StartRegistrationHandler(
            clock,
            memberInfoFactory,
            memberTypeChecker,
            membershipPersistenceClient,
            membershipQueryClient,
            cordaAvroSerializationFactory,
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
            assertThat(this.outputStates[1].key)
                .isEqualTo("${mgmHoldingIdentity.toCorda().shortHash}-${holdingIdentity.toCorda().shortHash}-$MEMBER_STATUS_PENDING")
        }
        verifyServices(
            persistRegistrationRequest = true,
            verify = true,
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
            verify = true,
        )
    }

    @Test
    fun `declined if target MGM is not an mgm`() {
        whenever(memberTypeChecker.getMgmMemberInfo(mgmHoldingIdentity.toCorda())).doReturn(null)

        with(handler.invoke(null, Record(testTopic, testTopicKey, startRegistrationCommand))) {
            assertThat(updatedState).isNotNull
            assertThat(updatedState!!.registrationId).isEqualTo(registrationId)
            assertThat(updatedState!!.registeringMember).isEqualTo(holdingIdentity)
            assertThat(outputStates).isNotEmpty.hasSize(1)

            assertDeclinedRegistration()
        }
        verifyServices(
            verify = true,
        )
    }

    @Test
    fun `declined if target member is an mgm`() {
        whenever(memberTypeChecker.isMgm(holdingIdentity.toCorda())).doReturn(true)

        with(handler.invoke(null, Record(testTopic, testTopicKey, startRegistrationCommand))) {
            assertThat(updatedState).isNotNull
            assertThat(updatedState!!.registrationId).isEqualTo(registrationId)
            assertThat(updatedState!!.registeringMember).isEqualTo(holdingIdentity)
            assertThat(outputStates).isNotEmpty.hasSize(1)

            assertDeclinedRegistration()
        }
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
            verify = true,
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
            verify = true,
        )
    }

    @Test
    fun `declined if member already exists`() {
        whenever(membershipQueryClient.queryMemberInfo(eq(mgmHoldingIdentity.toCorda()), any()))
            .doReturn(MembershipQueryResult.Success(listOf(memberInfo)))
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
            verify = true,
            queryMemberInfo = true,
        )
    }

    @Test
    fun `approve if member already exists but has DECLINED as last status`() {
        whenever(membershipQueryClient.queryMemberInfo(eq(mgmHoldingIdentity.toCorda()), any()))
            .doReturn(MembershipQueryResult.Success(listOf(memberInfo, declinedMember)))
        with(
            handler.invoke(null, Record(testTopic, testTopicKey, startRegistrationCommand))
        ) {
            assertThat(updatedState).isNotNull
            assertThat(updatedState!!.registrationId).isEqualTo(registrationId)
            assertThat(updatedState!!.registeringMember).isEqualTo(holdingIdentity)
            assertThat(outputStates).isNotEmpty.hasSize(2)

            assertRegistrationStarted()
        }
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
            verify = true,
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
            verify = true,
            queryMemberInfo = true,
            persistMemberInfo = true
        )
    }

    @Test
    fun `invoke returns follow on records when role is set to notary`() {
        val notaryDetails = MemberNotaryDetails(
            x500Name,
            "Notary Plugin A",
            listOf(mock())
        )
        whenever(memberMemberContext.parse<MemberNotaryDetails>("corda.notary")).thenReturn(notaryDetails)

        val result = handler.invoke(null, Record(testTopic, testTopicKey, startRegistrationCommand))

        result.assertRegistrationStarted()
    }

    @Test
    fun `declined if role is set to notary but notary keys are missing`() {
        val notaryDetails = MemberNotaryDetails(
            x500Name,
            null,
            emptyList()
        )
        whenever(memberMemberContext.parse<MemberNotaryDetails>("corda.notary")).thenReturn(notaryDetails)

        val result = handler.invoke(null, Record(testTopic, testTopicKey, startRegistrationCommand))

        result.assertDeclinedRegistration()
    }

    @Test
    fun `declined if role is set to notary and notary service plugin type is specified but blank`() {
        val notaryDetails = MemberNotaryDetails(
            x500Name,
            " ",
            listOf(mock())
        )
        whenever(memberMemberContext.parse<MemberNotaryDetails>("corda.notary")).thenReturn(notaryDetails)

        val result = handler.invoke(null, Record(testTopic, testTopicKey, startRegistrationCommand))

        result.assertDeclinedRegistration()
    }

    @Nested
    inner class PreAuthTokenTest {
        @Test
        fun `Invalid UUID for preauth token results in declined registration`() {
            whenever(memberMemberContext.parseOrNull(PRE_AUTH_TOKEN, UUID::class.java)).doThrow(
                IllegalArgumentException("bad-token")
            )

            val result = handler.invoke(null, Record(testTopic, testTopicKey, startRegistrationCommand))

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

            val result = handler.invoke(null, Record(testTopic, testTopicKey, startRegistrationCommand))

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

            val result = handler.invoke(null, Record(testTopic, testTopicKey, startRegistrationCommand))

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

            val result = handler.invoke(null, Record(testTopic, testTopicKey, startRegistrationCommand))

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

            val result = handler.invoke(null, Record(testTopic, testTopicKey, startRegistrationCommand))

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

            val result = handler.invoke(null, Record(testTopic, testTopicKey, startRegistrationCommand))

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

            val result = handler.invoke(null, Record(testTopic, testTopicKey, startRegistrationCommand))

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
        persistRegistrationRequest: Boolean = false,
        verify: Boolean = false,
        queryMemberInfo: Boolean = false,
        persistMemberInfo: Boolean = false
    ) {
        fun getVerificationMode(condition: Boolean) = if (condition) times(1) else never()

        verify(membershipPersistenceClient, getVerificationMode(persistRegistrationRequest))
            .persistRegistrationRequest(eq(mgmHoldingIdentity.toCorda()), any())

        verify(membershipQueryClient, getVerificationMode(queryMemberInfo))
            .queryMemberInfo(eq(mgmHoldingIdentity.toCorda()), any())

        verify(membershipPersistenceClient, getVerificationMode(persistMemberInfo))
            .persistMemberInfo(eq(mgmHoldingIdentity.toCorda()), any())

        verify(memberTypeChecker, getVerificationMode(verify))
            .getMgmMemberInfo(eq(mgmHoldingIdentity.toCorda()))
    }
}