package net.corda.membership.impl.registration.dynamic.mgm

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.SignatureSpecs.ECDSA_SHA256
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.CryptoConsts.Categories.PRE_AUTH
import net.corda.crypto.core.CryptoConsts.Categories.SESSION_INIT
import net.corda.crypto.core.ShortHash
import net.corda.crypto.impl.utils.toMap
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.impl.registration.TEST_CPI_NAME
import net.corda.membership.impl.registration.TEST_CPI_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.CREATION_TIME
import net.corda.membership.lib.MemberInfoExtension.Companion.ECDH_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_SIGNER_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEYS_PEM
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.PROTOCOL_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEYS_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEYS_SIGNATURE_SPEC
import net.corda.membership.lib.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoExtension.Companion.URL_KEY
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.SelfSignedMemberInfo
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceOperation
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.messaging.api.records.Record
import net.corda.test.util.TestRandom
import net.corda.test.util.time.TestClock
import net.corda.utilities.time.Clock
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.KeySchemeCodes
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
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
import java.util.UUID

class MGMRegistrationMemberInfoHandlerTest {

    private companion object {
        const val EMPTY_STRING = ""
        const val TEST_PLATFORM_VERSION = 5000
        const val TEST_SOFTWARE_VERSION = "5.0.0.0-test"
        const val GROUP_POLICY_PROPERTY_KEY = GROUP_POLICY_PREFIX_WITH_DOT + "test"
    }

    private val ecdhKeyId = "ABC123456789"
    private val sessionKeyId = "BBC123456789"
    private val sessionKeySchema = KeySchemeCodes.RSA_CODE_NAME

    private val holdingIdentity = HoldingIdentity(
        MemberX500Name.parse("O=Alice, L=London, C=GB"),
        UUID(0, 1).toString()
    )
    private val cpiIdentifier = CpiIdentifier(TEST_CPI_NAME, TEST_CPI_VERSION, TestRandom.secureHash())
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
        on { algorithm } doReturn "EC"
    }
    private val signature = CryptoSignatureWithKey(ByteBuffer.wrap(byteArrayOf()), ByteBuffer.wrap(byteArrayOf()))
    private val signatureSpec = CryptoSignatureSpec("", null, null)
    private val signedMemberInfo = mock<SelfSignedMemberInfo> {
        on { memberSignature } doReturn signature
        on { memberSignatureSpec } doReturn signatureSpec
    }
    private val contextCaptor = argumentCaptor<KeyValuePairList>()
    private val memberContext
        get() = assertDoesNotThrow { contextCaptor.firstValue.items.toMap() }
    private val mgmContext
        get() = assertDoesNotThrow { contextCaptor.secondValue.items.toMap() }

    private val clock: Clock = TestClock(Instant.ofEpochSecond(0))

    private val cryptoOpsClient: CryptoOpsClient = mock {
        on {
            lookupKeysByIds(
                eq(holdingIdentity.shortHash.value),
                argThat {
                    this.firstOrNull()?.value == ecdhKeyId
                }
            )
        } doReturn listOf(
            CryptoSigningKey(
                EMPTY_STRING,
                EMPTY_STRING,
                PRE_AUTH,
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
        on {
            lookupKeysByIds(
                eq(holdingIdentity.shortHash.value),
                argThat {
                    this.firstOrNull()?.value == sessionKeyId
                }
            )
        } doReturn listOf(
            CryptoSigningKey(
                EMPTY_STRING,
                EMPTY_STRING,
                SESSION_INIT,
                EMPTY_STRING,
                EMPTY_STRING,
                ByteBuffer.wrap(EMPTY_STRING.toByteArray()),
                sessionKeySchema,
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
    private val memberContextBytes = byteArrayOf(0)
    private val mgmContextBytes = byteArrayOf(1)
    private val serializer = mock<CordaAvroSerializer<KeyValuePairList>> {
        on {
            serialize(contextCaptor.capture())
        } doReturn memberContextBytes doReturn mgmContextBytes
    }
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroSerializer<KeyValuePairList>(any()) } doReturn serializer
    }
    private val memberInfoFactory: MemberInfoFactory = mock {
        on { createSelfSignedMemberInfo(memberContextBytes, mgmContextBytes, signature, signatureSpec) } doReturn signedMemberInfo
    }
    private val operation = mock<MembershipPersistenceOperation<Unit>> {
        on { execute() } doReturn MembershipPersistenceResult.success()
    }
    private val membershipPersistenceClient: MembershipPersistenceClient = mock {
        on {
            persistMemberInfo(eq(holdingIdentity), eq(listOf(signedMemberInfo)))
        } doReturn Operation(MembershipPersistenceResult.success())

        on {
            persistRegistrationRequest(any(), any())
        } doReturn operation
    }

    private val platformInfoProvider: PlatformInfoProvider = mock {
        on { activePlatformVersion } doReturn TEST_PLATFORM_VERSION
        on { localWorkerSoftwareVersion } doReturn TEST_SOFTWARE_VERSION
    }
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService = mock {
        on { get(eq(holdingIdentity)) } doReturn virtualNodeInfo
    }

    private val membershipQueryClient = mock<MembershipQueryClient> {
        on {
            queryMemberInfo(eq(holdingIdentity), eq(setOf(holdingIdentity)), eq(listOf(MEMBER_STATUS_ACTIVE)))
        } doReturn MembershipQueryResult.Success(listOf(signedMemberInfo))
    }

    private val mgmRegistrationMemberInfoHandler = MGMRegistrationMemberInfoHandler(
        clock,
        cryptoOpsClient,
        keyEncodingService,
        memberInfoFactory,
        membershipPersistenceClient,
        membershipQueryClient,
        platformInfoProvider,
        virtualNodeInfoReadService,
        cordaAvroSerializationFactory,
    )

    private val validTestContext
        get() = mapOf(
            SESSION_KEY_IDS.format(0) to sessionKeyId,
            ECDH_KEY_ID to ecdhKeyId,
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
            mgmRegistrationMemberInfoHandler.buildMgmMemberInfo(
                holdingIdentity,
                validTestContext,
            )
        }

        assertThat(result).isEqualTo(signedMemberInfo)
    }

    @Test
    fun `Expected services are called by buildMgmMemberInfo`() {
        assertDoesNotThrow {
            mgmRegistrationMemberInfoHandler.buildMgmMemberInfo(
                holdingIdentity,
                validTestContext,
            )
        }

        verify(memberInfoFactory).createSelfSignedMemberInfo(memberContextBytes, mgmContextBytes, signature, signatureSpec)
        verify(platformInfoProvider).activePlatformVersion
        verify(platformInfoProvider).localWorkerSoftwareVersion
        verify(keyEncodingService, times(2)).encodeAsString(any())
        verify(keyEncodingService, times(2)).decodePublicKey(any<ByteArray>())
        verify(cryptoOpsClient, times(2)).lookupKeysByIds(any(), any())
        verify(virtualNodeInfoReadService).get(any())
    }

    @Test
    fun `Expected services are called by persistMgmMemberInfo`() {
        assertDoesNotThrow {
            mgmRegistrationMemberInfoHandler.persistMgmMemberInfo(
                holdingIdentity,
                signedMemberInfo
            )
        }
        verify(membershipPersistenceClient).persistMemberInfo(any(), any())
    }

    @Test
    fun `Member context filters out group policy properties`() {
        assertDoesNotThrow {
            mgmRegistrationMemberInfoHandler.buildMgmMemberInfo(
                holdingIdentity,
                validTestContext,
            )
        }

        assertThat(memberContext).doesNotContainKey(GROUP_POLICY_PROPERTY_KEY)
    }

    @Test
    fun `Member context is built as expected`() {
        assertDoesNotThrow {
            mgmRegistrationMemberInfoHandler.buildMgmMemberInfo(
                holdingIdentity,
                validTestContext,
            )
        }

        assertThat(memberContext).containsOnlyKeys(
            GROUP_ID,
            PARTY_NAME,
            PARTY_SESSION_KEYS_PEM.format(0),
            SESSION_KEYS_HASH.format(0),
            ECDH_KEY,
            PLATFORM_VERSION,
            SOFTWARE_VERSION,
            MEMBER_CPI_NAME,
            MEMBER_CPI_VERSION,
            URL_KEY.format(0),
            PROTOCOL_VERSION.format(0),
            MEMBER_CPI_SIGNER_HASH
        )
    }

    @Test
    fun `MGM context is built as expected`() {
        assertDoesNotThrow {
            mgmRegistrationMemberInfoHandler.buildMgmMemberInfo(
                holdingIdentity,
                validTestContext,
            )
        }

        assertThat(mgmContext)
            .containsOnlyKeys(CREATION_TIME, MODIFIED_TIME, STATUS, IS_MGM, SERIAL)
            .containsEntry(STATUS, MEMBER_STATUS_ACTIVE)
            .containsEntry(IS_MGM, true.toString())
    }

    @Test
    fun `Signature is built as expected`() {
        val result = assertDoesNotThrow {
            mgmRegistrationMemberInfoHandler.buildMgmMemberInfo(
                holdingIdentity,
                validTestContext,
            )
        }

        SoftAssertions.assertSoftly {
            it.assertThat(result.memberSignature).isEqualTo(signature)
            it.assertThat(result.memberSignatureSpec).isEqualTo(signatureSpec)
        }
    }

    @Test
    fun `expected exception thrown if CPI info cannot be found for holding identity`() {
        whenever(
            virtualNodeInfoReadService.get(
                eq(holdingIdentity)
            )
        ).doReturn(null)
        assertThrows<MGMRegistrationMemberInfoHandlingException> {
            mgmRegistrationMemberInfoHandler.buildMgmMemberInfo(
                holdingIdentity,
                validTestContext,
            )
        }
        verify(virtualNodeInfoReadService).get(eq(holdingIdentity))
        verify(cryptoOpsClient, never()).lookupKeysByIds(any(), any())
    }

    @Test
    fun `expected exception thrown if key cannot be found for holding identity`() {
        whenever(
            cryptoOpsClient.lookupKeysByIds(
                eq(holdingIdentity.shortHash.value),
                eq(listOf(ShortHash.of(sessionKeyId)))
            )
        ).doReturn(emptyList())

        assertThrows<MGMRegistrationMemberInfoHandlingException> {
            mgmRegistrationMemberInfoHandler.buildMgmMemberInfo(
                holdingIdentity,
                validTestContext,
            )
        }
        verify(virtualNodeInfoReadService).get(eq(holdingIdentity))
        verify(cryptoOpsClient).lookupKeysByIds(
            eq(holdingIdentity.shortHash.value),
            eq(listOf(ShortHash.of(sessionKeyId)))
        )
    }

    @Test
    fun `expected exception thrown if key cannot be decoded`() {
        whenever(
            keyEncodingService.decodePublicKey(any<ByteArray>())
        ).doThrow(RuntimeException::class)

        assertThrows<MGMRegistrationMemberInfoHandlingException> {
            mgmRegistrationMemberInfoHandler.buildMgmMemberInfo(
                holdingIdentity,
                validTestContext,
            )
        }
        verify(virtualNodeInfoReadService).get(eq(holdingIdentity))
        verify(cryptoOpsClient).lookupKeysByIds(any(), any())
        verify(keyEncodingService).decodePublicKey(any<ByteArray>())
    }

    @Test
    fun `expected exception thrown if member info persistence fails`() {
        whenever(
            membershipPersistenceClient.persistMemberInfo(
                eq(holdingIdentity),
                eq(listOf(signedMemberInfo))
            )
        ).doReturn(Operation(MembershipPersistenceResult.Failure("")))

        assertThrows<MGMRegistrationMemberInfoHandlingException> {
            mgmRegistrationMemberInfoHandler.persistMgmMemberInfo(
                holdingIdentity,
                signedMemberInfo
            )
        }
        verify(membershipPersistenceClient).persistMemberInfo(
            eq(holdingIdentity),
            eq(listOf(signedMemberInfo))
        )
    }

    @Test
    fun `non EC algorithm ECDH key will cause an exception`() {
        val encryptedPublicKey = byteArrayOf(1, 2, 4)
        val ecdhPublicKey = mock<PublicKey> {
            on { encoded } doReturn EMPTY_STRING.toByteArray()
            on { algorithm } doReturn "RSA"
        }
        whenever(
            cryptoOpsClient.lookupKeysByIds(
                holdingIdentity.shortHash.value,
                listOf(
                    ShortHash.of(ecdhKeyId)
                )
            )
        ).doReturn(
            listOf(
                CryptoSigningKey(
                    EMPTY_STRING,
                    EMPTY_STRING,
                    PRE_AUTH,
                    EMPTY_STRING,
                    EMPTY_STRING,
                    ByteBuffer.wrap(encryptedPublicKey),
                    EMPTY_STRING,
                    EMPTY_STRING,
                    0,
                    EMPTY_STRING,
                    Instant.ofEpochSecond(0)
                )
            )
        )
        whenever(keyEncodingService.decodePublicKey(encryptedPublicKey)).doReturn(ecdhPublicKey)

        assertThrows<MGMRegistrationContextValidationException> {
            mgmRegistrationMemberInfoHandler.buildMgmMemberInfo(
                holdingIdentity,
                validTestContext,
            )
        }
    }

    @Test
    fun `session key with the wrong category will cause an exception`() {
        whenever(
            cryptoOpsClient.lookupKeysByIds(
                holdingIdentity.shortHash.value,
                listOf(
                    ShortHash.of(sessionKeyId)
                )
            )
        ).doReturn(
            listOf(
                CryptoSigningKey(
                    EMPTY_STRING,
                    EMPTY_STRING,
                    PRE_AUTH,
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
        )

        assertThrows<MGMRegistrationContextValidationException> {
            mgmRegistrationMemberInfoHandler.buildMgmMemberInfo(
                holdingIdentity,
                validTestContext,
            )
        }
    }

    @Test
    fun `ECDH key with the wrong category will cause an exception`() {
        whenever(
            cryptoOpsClient.lookupKeysByIds(
                holdingIdentity.shortHash.value,
                listOf(
                    ShortHash.of(ecdhKeyId)
                )
            )
        ).doReturn(
            listOf(
                CryptoSigningKey(
                    EMPTY_STRING,
                    EMPTY_STRING,
                    SESSION_INIT,
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
        )

        assertThrows<MGMRegistrationContextValidationException> {
            mgmRegistrationMemberInfoHandler.buildMgmMemberInfo(
                holdingIdentity,
                validTestContext,
            )
        }
    }

    @Test
    fun `session key with unsupported key scheme will cause an exception`() {
        whenever(
            cryptoOpsClient.lookupKeysByIds(
                holdingIdentity.shortHash.value,
                listOf(
                    ShortHash.of(sessionKeyId)
                )
            )
        ).doReturn(
            listOf(
                CryptoSigningKey(
                    EMPTY_STRING,
                    EMPTY_STRING,
                    SESSION_INIT,
                    EMPTY_STRING,
                    EMPTY_STRING,
                    ByteBuffer.wrap(EMPTY_STRING.toByteArray()),
                    KeySchemeCodes.EDDSA_ED25519_CODE_NAME,
                    EMPTY_STRING,
                    0,
                    EMPTY_STRING,
                    Instant.ofEpochSecond(0)
                )
            )
        )

        assertThrows<MGMRegistrationContextValidationException> {
            mgmRegistrationMemberInfoHandler.buildMgmMemberInfo(
                holdingIdentity,
                validTestContext
            )
        }
    }

    @Test
    fun `session key with unsupported key scheme and signature spec combination will cause an exception`() {
        // this test relies on the session key scheme being mocked to be incompatible with the signature spec so this assertion verifies
        // the value isn't changed
        assertThat(sessionKeySchema).isEqualTo(KeySchemeCodes.RSA_CODE_NAME)
        assertThrows<MGMRegistrationContextValidationException> {
            mgmRegistrationMemberInfoHandler.buildMgmMemberInfo(
                holdingIdentity,
                validTestContext + mapOf(SESSION_KEYS_SIGNATURE_SPEC.format(0) to ECDSA_SHA256.signatureName)
            )
        }
    }

    @Test
    fun `querying for mgm info is successful`() {
        assertThat(mgmRegistrationMemberInfoHandler.queryForMGMMemberInfo(holdingIdentity)).isEqualTo(signedMemberInfo)
    }

    @Test
    fun `build MGM info keeps original creation time and sets the serial as expected`() {
        val serial = 2L
        val creationTime = clock.instant()
        mgmRegistrationMemberInfoHandler.buildMgmMemberInfo(
            holdingIdentity,
            validTestContext,
            serial,
            creationTime.toString(),
        )

        assertThat(mgmContext)
            .containsEntry(SERIAL, "2")
            .containsEntry(CREATION_TIME, creationTime.toString())
    }

    private class Operation(
        private val value: MembershipPersistenceResult<Unit>
    ) : MembershipPersistenceOperation<Unit> {
        override fun execute() = value

        override fun createAsyncCommands(): Collection<Record<*, *>> {
            return emptyList()
        }
    }
}
