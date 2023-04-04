package net.corda.membership.lib.impl.converter

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.impl.CompositeKeyProviderImpl
import net.corda.crypto.impl.converter.PublicKeyConverter
import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.KeyUtils
import net.corda.v5.membership.NotaryInfo
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.util.SortedMap

class NotaryInfoConverterIntegrationTest {
    @Suppress("SpreadOperator")
    private companion object {
        val notaryService = MemberX500Name.parse("O=NotaryService,L=London,C=GB")
        const val NOTARY_PROTOCOL = "testProtocol"
        const val NAME = "name"
        const val PROTOCOL = "flow.protocol.name"
        const val PROTOCOL_VERSIONS = "flow.protocol.version.%s"
        const val KEYS = "keys.%s"
        const val KEY_VALUE_1 = "encoded_key1"
        const val KEY_VALUE_2 = "encoded_key2"
        val key1: PublicKey = KeyPairGenerator.getInstance("DSA").genKeyPair().public
        val key2: PublicKey = KeyPairGenerator.getInstance("DSA").genKeyPair().public
        val keyEncodingService: KeyEncodingService = mock {
            on { encodeAsString(key1) } doReturn KEY_VALUE_1
            on { encodeAsString(key2) } doReturn KEY_VALUE_2
            on { decodePublicKey(KEY_VALUE_1) } doReturn key1
            on { decodePublicKey(KEY_VALUE_2) } doReturn key2
        }
        val notaryKeys = listOf(key1, key2)

        val correctContext = sortedMapOf(
            NAME to notaryService.toString(),
            PROTOCOL to NOTARY_PROTOCOL,
            *convertNotaryProtocolVersions().toTypedArray(),
            *convertNotaryKeys().toTypedArray()
        )

        val converters = listOf(
            NotaryInfoConverter(CompositeKeyProviderImpl()),
            PublicKeyConverter(keyEncodingService)
        )

        fun convertNotaryKeys(): List<Pair<String, String>> =
            notaryKeys.mapIndexed { i, notaryKey ->
                String.format(
                    KEYS,
                    i
                ) to keyEncodingService.encodeAsString(notaryKey)
            }

        fun convertNotaryProtocolVersions(): List<Pair<String, String>> = List(3) { index ->
            String.format(
                PROTOCOL_VERSIONS, index
            ) to (index + 1).toString()
        }
    }

    private fun convertToNotaryInfo(context: SortedMap<String, String>): NotaryInfo =
        converters.find { it.type == NotaryInfo::class.java }?.convert(
            LayeredPropertyMapMocks.createConversionContext<LayeredContextImpl>(
                context,
                converters,
                ""
            )
        ) as NotaryInfo

    @Test
    fun `converter converts notary service info successfully for multiple notary vnodes`() {
        val result = convertToNotaryInfo(correctContext)
        assertSoftly {
            it.assertThat(result.name).isEqualTo(notaryService)
            it.assertThat(result.protocol).isEqualTo(NOTARY_PROTOCOL)
            it.assertThat(result.protocolVersions).containsExactlyInAnyOrder(1, 2, 3)
            // Any notary vnode key should satisfy the composite key
            it.assertThat(KeyUtils.isKeyFulfilledBy(result.publicKey, key1)).isEqualTo(true)
            it.assertThat(KeyUtils.isKeyFulfilledBy(result.publicKey, key2)).isEqualTo(true)
        }
    }
}
