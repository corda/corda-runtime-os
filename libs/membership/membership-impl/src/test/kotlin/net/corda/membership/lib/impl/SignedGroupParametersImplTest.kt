package net.corda.membership.lib.impl

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.core.CompositeKeyProvider
import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.crypto.impl.converter.PublicKeyConverter
import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.EPOCH_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.MODIFIED_TIME_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.NOTARY_SERVICE_KEYS_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.NOTARY_SERVICE_NAME_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.NOTARY_SERVICE_PROTOCOL_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY
import net.corda.membership.lib.impl.converter.NotaryInfoConverter
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.CompositeKeyNodeAndWeight
import net.corda.v5.crypto.SignatureSpec
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.security.PublicKey
import java.time.Instant

class SignedGroupParametersImplTest {
    private companion object {
        val clock = TestClock(Instant.ofEpochSecond(100))
        val modifiedTime = clock.instant()
        const val VALID_VALUE = 1
        val notaryAName = MemberX500Name.parse("C=GB, L=London, O=NotaryServiceA")
        val notaryBName = MemberX500Name.parse("C=GB, L=London, O=NotaryServiceB")
        const val NOTARY_PROTOCOL = "notaryPlugin"
        const val KEY = "key"
    }

    private val key: PublicKey = mock()
    private val compositeKeyNodeAndWeight = CompositeKeyNodeAndWeight(key, 1)
    private val compositeKey: PublicKey = mock()
    private val keyEncodingService: KeyEncodingService = mock {
        on { decodePublicKey(KEY) } doReturn key
    }
    private val compositeKeyProvider: CompositeKeyProvider = mock {
        on { create(eq(listOf(compositeKeyNodeAndWeight)), any()) } doReturn compositeKey
    }

    private class TestLayeredPropertyMap(
        map: LayeredPropertyMap
    ) : LayeredPropertyMap by map

    private val serializedParameters = "group-params".toByteArray()
    private val signature: DigitalSignatureWithKey = mock()
    private val signatureSpec: SignatureSpec = mock()

    private fun createTestParams(
        serializedParams: ByteArray = serializedParameters,
        sig: DigitalSignatureWithKey = signature,
        sigSpec: SignatureSpec = signatureSpec,
        epoch: Int = VALID_VALUE,
        time: Instant = modifiedTime
    ) = SignedGroupParametersImpl(
        serializedParams,
        sig,
        sigSpec
    ) {
        LayeredPropertyMapMocks.create<TestLayeredPropertyMap>(
            sortedMapOf(
                EPOCH_KEY to epoch.toString(),
                MODIFIED_TIME_KEY to time.toString(),
                String.format(NOTARY_SERVICE_NAME_KEY, 0) to notaryAName.toString(),
                String.format(NOTARY_SERVICE_PROTOCOL_KEY, 0) to NOTARY_PROTOCOL,
                String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 0, 0) to "1",
                String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 0, 1) to "2",
                String.format(NOTARY_SERVICE_KEYS_KEY, 0, 0) to KEY,
                String.format(NOTARY_SERVICE_NAME_KEY, 1) to notaryBName.toString(),
                String.format(NOTARY_SERVICE_PROTOCOL_KEY, 1) to NOTARY_PROTOCOL,
                String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 1, 0) to "2",
                String.format(NOTARY_SERVICE_KEYS_KEY, 1, 0) to KEY
            ),
            listOf(NotaryInfoConverter(compositeKeyProvider), PublicKeyConverter(keyEncodingService))
        )
    }

    @Test
    fun `group parameters are created successfully`() {
        val params = createTestParams()
        assertSoftly {
            it.assertThat(params.epoch).isEqualTo(VALID_VALUE)
            it.assertThat(params.modifiedTime).isEqualTo(modifiedTime)
            it.assertThat(params.notaries)
                .hasSize(2)
                .anySatisfy { notary ->
                    it.assertThat(notary.name).isEqualTo(notaryAName)
                    it.assertThat(notary.protocol).isEqualTo(NOTARY_PROTOCOL)
                    it.assertThat(notary.publicKey).isEqualTo(compositeKey)
                    it.assertThat(notary.protocolVersions).containsExactlyInAnyOrder(1, 2)
                }
                .anySatisfy { notary ->
                    it.assertThat(notary.name).isEqualTo(notaryBName)
                    it.assertThat(notary.protocol).isEqualTo(NOTARY_PROTOCOL)
                    it.assertThat(notary.publicKey).isEqualTo(compositeKey)
                    it.assertThat(notary.protocolVersions).containsExactlyInAnyOrder(2)
                }

            it.assertThat(params.mgmSignature).isEqualTo(signature)
            it.assertThat(params.mgmSignatureSpec).isEqualTo(signatureSpec)
        }
    }

    @Test
    fun `group parameters with the same serialised parameters, signature and signature spec are equal`() {
        val firstParams = createTestParams()
        val secondParams = createTestParams()

        assertThat(firstParams).isEqualTo(secondParams)
        assertThat(firstParams.hashCode()).isEqualTo(secondParams.hashCode())
        assertThat(firstParams).isNotSameAs(secondParams)
    }

    @Test
    fun `group parameters with different serialised parameters are not equal`() {
        val firstParams = createTestParams()
        val secondParams = createTestParams(
            serializedParams = "other-params".toByteArray()
        )

        assertThat(firstParams).isNotEqualTo(secondParams)
        assertThat(firstParams.hashCode()).isNotEqualTo(secondParams.hashCode())
    }

    @Test
    fun `group parameters with different signature are not equal`() {
        val firstParams = createTestParams()
        val secondParams = createTestParams(
            sig = mock()
        )

        assertThat(firstParams).isNotEqualTo(secondParams)
        assertThat(firstParams.hashCode()).isNotEqualTo(secondParams.hashCode())
    }

    @Test
    fun `group parameters with different signature spec are not equal`() {
        val firstParams = createTestParams()
        val secondParams = createTestParams(
            sigSpec = mock()
        )

        assertThat(firstParams).isNotEqualTo(secondParams)
        assertThat(firstParams.hashCode()).isNotEqualTo(secondParams.hashCode())
    }

    @Test
    fun `same group parameters instances are equal`() {
        val firstParams = createTestParams()

        assertThat(firstParams).isEqualTo(firstParams)
        assertThat(firstParams.hashCode()).isEqualTo(firstParams.hashCode())
        assertThat(firstParams).isSameAs(firstParams)
    }
}
