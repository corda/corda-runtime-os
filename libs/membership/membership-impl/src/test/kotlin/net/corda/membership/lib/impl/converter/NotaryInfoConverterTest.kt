package net.corda.membership.lib.impl.converter

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.core.CompositeKeyProvider
import net.corda.crypto.impl.converter.PublicKeyConverter
import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.v5.base.exceptions.ValueNotFoundException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.CompositeKeyNodeAndWeight
import net.corda.v5.membership.NotaryInfo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.security.PublicKey
import java.util.SortedMap

class NotaryInfoConverterTest {
    @Suppress("SpreadOperator")
    private companion object {
        val notaryService = MemberX500Name.parse("O=NotaryService,L=London,C=GB")
        const val NOTARY_PROTOCOL = "testProtocol"
        const val NAME = "name"
        const val PROTOCOL = "flow.protocol.name"
        const val PROTOCOL_VERSIONS = "flow.protocol.version.%s"
        const val KEYS = "keys.%s"
        const val KEY_VALUE = "encoded_key"
        val key: PublicKey = mock()
        val keyEncodingService: KeyEncodingService = mock {
            on { encodeAsString(key) } doReturn KEY_VALUE
            on { decodePublicKey(KEY_VALUE) } doReturn key
        }
        val notaryKeys = listOf(key, key)
        val compositeKeyForNonEmptyKeys: PublicKey = mock()
        val compositeKeyForEmptyKeys: PublicKey = mock()
        val weightedKeys = notaryKeys.map {
            CompositeKeyNodeAndWeight(it, 1)
        }
        val compositeKeyProvider: CompositeKeyProvider = mock {
            on { create(eq(weightedKeys), eq(null)) } doReturn compositeKeyForNonEmptyKeys
            on { create(eq(emptyList()), eq(null)) } doReturn compositeKeyForEmptyKeys
        }

        val correctContext = sortedMapOf(
            NAME to notaryService.toString(),
            PROTOCOL to NOTARY_PROTOCOL,
            *convertNotaryProtocolVersions().toTypedArray(),
            *convertNotaryKeys().toTypedArray()
        )

        val contextWithoutName = sortedMapOf(
            PROTOCOL to NOTARY_PROTOCOL,
            *convertNotaryProtocolVersions().toTypedArray(),
            *convertNotaryKeys().toTypedArray()
        )

        val contextWithoutProtocol = sortedMapOf(
            NAME to notaryService.toString(),
            *convertNotaryProtocolVersions().toTypedArray(),
            *convertNotaryKeys().toTypedArray()
        )

        val contextWithoutKeys = sortedMapOf(
            NAME to notaryService.toString(),
            PROTOCOL to NOTARY_PROTOCOL,
            *convertNotaryProtocolVersions().toTypedArray(),
        )

        val contextWithoutVersions = sortedMapOf(
            NAME to notaryService.toString(),
            PROTOCOL to NOTARY_PROTOCOL,
            *convertNotaryKeys().toTypedArray(),
        )

        val converters = listOf(
            NotaryInfoConverter(compositeKeyProvider),
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
    fun `converter converts notary service info successfully`() {
        val result = convertToNotaryInfo(correctContext)
        assertSoftly {
            it.assertThat(result.name).isEqualTo(notaryService)
            it.assertThat(result.protocol).isEqualTo(NOTARY_PROTOCOL)
            it.assertThat(result.protocolVersions).containsExactlyInAnyOrder(1, 2, 3)
            it.assertThat(result.publicKey).isEqualTo(compositeKeyForNonEmptyKeys)
        }
    }

    @Test
    fun `converter converts notary service info successfully even if the notary keys are empty`() {
        val result = convertToNotaryInfo(contextWithoutKeys)
        assertSoftly {
            it.assertThat(result.name).isEqualTo(notaryService)
            it.assertThat(result.protocol).isEqualTo(NOTARY_PROTOCOL)
            it.assertThat(result.protocolVersions).containsExactlyInAnyOrder(1, 2, 3)
            it.assertThat(result.publicKey).isEqualTo(compositeKeyForEmptyKeys)
        }
    }

    @Test
    fun `exception is thrown when notary service's name is missing`() {
        val ex = assertThrows<ValueNotFoundException> {
            convertToNotaryInfo(contextWithoutName)
        }
        assertThat(ex.message).contains("name")
    }

    @Test
    fun `exception is thrown when notary service's protocol is missing`() {
        val ex = assertThrows<ValueNotFoundException> {
            convertToNotaryInfo(contextWithoutProtocol)
        }
        assertThat(ex.message).contains("flow.protocol.name")
    }

    @Test
    fun `exception is thrown when notary service's protocol versions list is missing`() {
        val ex = assertThrows<ValueNotFoundException> {
            convertToNotaryInfo(contextWithoutVersions)
        }
        assertThat(ex.message).contains("flow.protocol.version")
    }
}