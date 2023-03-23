package net.corda.membership.lib.impl

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.core.CompositeKeyProvider
import net.corda.crypto.impl.converter.PublicKeyConverter
import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.membership.lib.EPOCH_KEY
import net.corda.membership.lib.MODIFIED_TIME_KEY
import net.corda.membership.lib.impl.converter.NotaryInfoConverter
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.CompositeKeyNodeAndWeight
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
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
        val notaryName = MemberX500Name.parse("C=GB, L=London, O=NotaryService")
        const val PLUGIN = "notaryPlugin"
        const val KEY = "key"
    }

    private val key: PublicKey = mock()
    private val compositeKeyNodeAndWeight = CompositeKeyNodeAndWeight(key, 1)
    private val compositeKey: PublicKey = mock()
    private val keyEncodingService: KeyEncodingService = mock {
        on { decodePublicKey(KEY) } doReturn key
    }
    private val compositeKeyProvider: CompositeKeyProvider = mock {
        on { create(eq(listOf(compositeKeyNodeAndWeight)), eq(null)) } doReturn compositeKey
    }


    private class TestLayeredPropertyMap(
        map: LayeredPropertyMap
    ) : LayeredPropertyMap by map

    private val signature: DigitalSignature.WithKey = mock()
    private val signatureSpec: SignatureSpec = mock()

    private fun createTestParams(
        serializedParams: ByteArray = "group-params".toByteArray(),
        sig: DigitalSignature.WithKey = signature,
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
                "corda.notary.service.0.name" to notaryName.toString(),
                "corda.notary.service.0.plugin" to PLUGIN,
                "corda.notary.service.0.keys.0" to KEY
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
            it.assertThat(params.notaries).hasSize(1)
            val notary = params.notaries.single()
            it.assertThat(notary.name).isEqualTo(notaryName)
            it.assertThat(notary.pluginClass).isEqualTo(PLUGIN)
            it.assertThat(notary.publicKey).isEqualTo(compositeKey)

            it.assertThat(params.signature).isEqualTo(signature)
            it.assertThat(params.signatureSpec).isEqualTo(signatureSpec)
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