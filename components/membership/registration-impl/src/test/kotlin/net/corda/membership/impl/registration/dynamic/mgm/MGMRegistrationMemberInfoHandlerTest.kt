package net.corda.membership.impl.registration.dynamic.mgm

import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.impl.registration.TEST_CPI_NAME
import net.corda.membership.impl.registration.TEST_CPI_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.CREATED_TIME
import net.corda.membership.lib.MemberInfoExtension.Companion.ECDH_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.PROTOCOL_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEY_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoExtension.Companion.URL_KEY
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.test.util.time.TestClock
import net.corda.utilities.time.Clock
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
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
import java.security.PublicKey
import java.time.Instant
import java.util.*

class MGMRegistrationMemberInfoHandlerTest {

    companion object {
        const val EMPTY_STRING = ""
        const val TEST_PLATFORM_VERSION = 5000
        const val TEST_SOFTWARE_VERSION = "5.0.0.0-test"
        const val GROUP_POLICY_PROPERTY_KEY = GROUP_POLICY_PREFIX_WITH_DOT + "test"
    }

    private val registrationId = UUID(0, 1)
    private val cordaAvroSerializer: CordaAvroSerializer<KeyValuePairList> = mock {
        on { serialize(any()) } doReturn "".toByteArray()
    }
    private val holdingIdentity = HoldingIdentity(
        MemberX500Name.parse("O=Alice, L=London, C=GB"),
        UUID(0, 1).toString()
    )
    private val cpiIdentifier = CpiIdentifier(TEST_CPI_NAME, TEST_CPI_VERSION, null)
    private val virtualNodeInfo = VirtualNodeInfo(
        holdingIdentity,
        cpiIdentifier,
        vaultDmlConnectionId = UUID(0, 1),
        cryptoDmlConnectionId = UUID(0, 1),
        uniquenessDmlConnectionId = UUID(0, 1),
        timestamp = Instant.ofEpochSecond(0)
    )
    private val publicKey: PublicKey = mock {
        on { encoded } doReturn EMPTY_STRING.toByteArray()
    }
    private val mockMemberContext: MemberContext = mock()
    private val memberInfo: MemberInfo = mock {
        on { memberProvidedContext } doReturn mockMemberContext
    }
    private val memberContextCaptor = argumentCaptor<SortedMap<String, String?>>()
    private val memberContext
        get() = assertDoesNotThrow { memberContextCaptor.firstValue }
    private val mgmContextCaptor = argumentCaptor<SortedMap<String, String?>>()
    private val mgmContext
        get() = assertDoesNotThrow { mgmContextCaptor.firstValue }


    private val clock: Clock = TestClock(Instant.ofEpochSecond(0))
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory = mock {
        on { createAvroSerializer<KeyValuePairList>(any()) } doReturn cordaAvroSerializer
    }
    private val cryptoOpsClient: CryptoOpsClient = mock {
        on {
            lookup(eq(holdingIdentity.shortHash.value), any())
        } doReturn listOf(
            CryptoSigningKey(
                EMPTY_STRING,
                EMPTY_STRING,
                EMPTY_STRING,
                EMPTY_STRING,
                EMPTY_STRING,
                ByteBuffer.wrap(EMPTY_STRING.toByteArray()),
                EMPTY_STRING,
                EMPTY_STRING,
                0,
                EMPTY_STRING,
                Instant.ofEpochSecond(0)
            )
        )
    }
    private val keyEncodingService: KeyEncodingService = mock {
        on { decodePublicKey(any<ByteArray>()) } doReturn publicKey
        on { encodeAsString(any()) } doReturn EMPTY_STRING
    }
    private val memberInfoFactory: MemberInfoFactory = mock {
        on { create(memberContextCaptor.capture(), mgmContextCaptor.capture()) } doReturn memberInfo
    }
    private val membershipPersistenceClient: MembershipPersistenceClient = mock {
        on {
            persistMemberInfo(eq(holdingIdentity), eq(listOf(memberInfo)))
        } doReturn MembershipPersistenceResult.success()

        on {
            persistRegistrationRequest(any(), any())
        } doReturn MembershipPersistenceResult.success()
    }

    private val platformInfoProvider: PlatformInfoProvider = mock {
        on { activePlatformVersion } doReturn TEST_PLATFORM_VERSION
        on { localWorkerSoftwareVersion } doReturn TEST_SOFTWARE_VERSION
    }
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService = mock {
        on { get(eq(holdingIdentity)) } doReturn virtualNodeInfo
    }

    private val mgmRegistrationMemberInfoHandler = MGMRegistrationMemberInfoHandler(
        clock,
        cordaAvroSerializationFactory,
        cryptoOpsClient,
        keyEncodingService,
        memberInfoFactory,
        membershipPersistenceClient,
        platformInfoProvider,
        virtualNodeInfoReadService
    )

    private val validTestContext
        get() = mapOf(
            SESSION_KEY_ID to "session key",
            ECDH_KEY_ID to "ECDH key",
            REGISTRATION_PROTOCOL to "registration protocol",
            SYNCHRONISATION_PROTOCOL to "synchronisation protocol",
            P2P_MODE to "P2P mode",
            SESSION_KEY_POLICY to "session key policy",
            PKI_SESSION to "session PKI property",
            PKI_TLS to "TLS PKI property",
            URL_KEY.format(0) to "https://localhost:8080",
            PROTOCOL_VERSION.format(0) to "1",
            TRUSTSTORE_SESSION.format(0) to "session truststore",
            TRUSTSTORE_TLS.format(0) to "tls truststore",
            GROUP_POLICY_PROPERTY_KEY to "should be filtered out"
        )

    @Test
    fun `MGM info is returned if all is processed successfully`() {
        val result = assertDoesNotThrow {
            mgmRegistrationMemberInfoHandler.buildAndPersist(
                registrationId,
                holdingIdentity,
                validTestContext
            )
        }

        assertThat(result).isEqualTo(memberInfo)
    }

    @Test
    fun `Expected services are called`() {
        assertDoesNotThrow {
            mgmRegistrationMemberInfoHandler.buildAndPersist(
                registrationId,
                holdingIdentity,
                validTestContext
            )
        }

        verify(membershipPersistenceClient).persistRegistrationRequest(any(), any())
        verify(membershipPersistenceClient).persistMemberInfo(any(), any())
        verify(cordaAvroSerializer).serialize(any())
        verify(memberInfoFactory).create(any(), any<SortedMap<String, String?>>())
        verify(platformInfoProvider).activePlatformVersion
        verify(platformInfoProvider).localWorkerSoftwareVersion
        verify(keyEncodingService, times(2)).encodeAsString(any())
        verify(keyEncodingService, times(2)).decodePublicKey(any<ByteArray>())
        verify(cryptoOpsClient, times(2)).lookup(any(), any())
        verify(virtualNodeInfoReadService).get(any())
    }

    @Test
    fun `Member context filters out group policy properties`() {
        assertDoesNotThrow {
            mgmRegistrationMemberInfoHandler.buildAndPersist(
                registrationId,
                holdingIdentity,
                validTestContext
            )
        }

        assertThat(memberContext).doesNotContainKey(GROUP_POLICY_PROPERTY_KEY)
    }

    @Test
    fun `Member context is built as expected`() {
        assertDoesNotThrow {
            mgmRegistrationMemberInfoHandler.buildAndPersist(
                registrationId,
                holdingIdentity,
                validTestContext
            )
        }

        assertThat(memberContext).containsOnlyKeys(
            GROUP_ID,
            PARTY_NAME,
            PARTY_SESSION_KEY,
            SESSION_KEY_HASH,
            ECDH_KEY,
            PLATFORM_VERSION,
            SOFTWARE_VERSION,
            MEMBER_CPI_NAME,
            MEMBER_CPI_VERSION,
            SERIAL,
            URL_KEY.format(0),
            PROTOCOL_VERSION.format(0)
        )
    }

    @Test
    fun `MGM context is built as expected`() {
        assertDoesNotThrow {
            mgmRegistrationMemberInfoHandler.buildAndPersist(
                registrationId,
                holdingIdentity,
                validTestContext
            )
        }

        assertThat(mgmContext)
            .containsOnlyKeys(CREATED_TIME, MODIFIED_TIME, STATUS, IS_MGM)
            .containsEntry(STATUS, MEMBER_STATUS_ACTIVE)
            .containsEntry(IS_MGM, true.toString())
    }

    @Test
    fun `expected exception thrown if CPI info cannot be found for holding identity`() {
        whenever(
            virtualNodeInfoReadService.get(
                eq(holdingIdentity)
            )
        ).doReturn(null)
        assertThrows<MGMRegistrationMemberInfoHandlingException> {
            mgmRegistrationMemberInfoHandler.buildAndPersist(
                registrationId,
                holdingIdentity,
                validTestContext
            )
        }
        verify(virtualNodeInfoReadService).get(eq(holdingIdentity))
        verify(cryptoOpsClient, never()).lookup(any(), any())
    }

    @Test
    fun `expected exception thrown if key cannot be found for holding identity`() {
        whenever(
            cryptoOpsClient.lookup(
                eq(holdingIdentity.shortHash.value),
                eq(listOf(validTestContext[SESSION_KEY_ID]!!))
            )
        ).doReturn(emptyList())

        assertThrows<MGMRegistrationMemberInfoHandlingException> {
            mgmRegistrationMemberInfoHandler.buildAndPersist(
                registrationId,
                holdingIdentity,
                validTestContext
            )
        }
        verify(virtualNodeInfoReadService).get(eq(holdingIdentity))
        verify(cryptoOpsClient).lookup(
            eq(holdingIdentity.shortHash.value),
            eq(listOf(validTestContext[SESSION_KEY_ID]!!))
        )
        verify(keyEncodingService, never()).decodePublicKey(any<ByteArray>())
    }

    @Test
    fun `expected exception thrown if key cannot be decoded`() {
        whenever(
            keyEncodingService.decodePublicKey(any<ByteArray>())
        ).doThrow(RuntimeException::class)

        assertThrows<MGMRegistrationMemberInfoHandlingException> {
            mgmRegistrationMemberInfoHandler.buildAndPersist(
                registrationId,
                holdingIdentity,
                validTestContext
            )
        }
        verify(virtualNodeInfoReadService).get(eq(holdingIdentity))
        verify(cryptoOpsClient).lookup(any(), any())
        verify(keyEncodingService).decodePublicKey(any<ByteArray>())
    }

    @Test
    fun `expected exception thrown if member info persistence fails`() {
        whenever(
            membershipPersistenceClient.persistMemberInfo(
                eq(holdingIdentity), eq(listOf(memberInfo))
            )
        ).doReturn(MembershipPersistenceResult.Failure(""))

        assertThrows<MGMRegistrationMemberInfoHandlingException> {
            mgmRegistrationMemberInfoHandler.buildAndPersist(
                registrationId,
                holdingIdentity,
                validTestContext
            )
        }
        verify(membershipPersistenceClient).persistMemberInfo(
            eq(holdingIdentity),
            eq(listOf(memberInfo))
        )
    }

    @Test
    fun `expected exception thrown if registration request persistence fails`() {
        whenever(
            membershipPersistenceClient.persistRegistrationRequest(
                eq(holdingIdentity), any()
            )
        ).doReturn(MembershipPersistenceResult.Failure(""))

        assertThrows<MGMRegistrationMemberInfoHandlingException> {
            mgmRegistrationMemberInfoHandler.buildAndPersist(
                registrationId,
                holdingIdentity,
                validTestContext
            )
        }
        verify(membershipPersistenceClient).persistRegistrationRequest(
            eq(holdingIdentity),
            any()
        )
    }

    @Test
    fun `expected exception thrown if serializing the registration request fails`() {
        whenever(
            cordaAvroSerializer.serialize(
                any()
            )
        ).doReturn(null)

        assertThrows<MGMRegistrationMemberInfoHandlingException> {
            mgmRegistrationMemberInfoHandler.buildAndPersist(
                registrationId,
                holdingIdentity,
                validTestContext
            )
        }
        verify(cordaAvroSerializer).serialize(any())
    }


}