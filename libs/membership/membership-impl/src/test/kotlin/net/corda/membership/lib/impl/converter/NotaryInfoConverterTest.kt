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
        const val NOTARY_PLUGIN = "testPlugin"
        const val NAME = "name"
        const val PLUGIN = "plugin"
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
            PLUGIN to NOTARY_PLUGIN,
            *convertNotaryKeys().toTypedArray()
        )

        val contextWithoutName = sortedMapOf(
            PLUGIN to NOTARY_PLUGIN,
            *convertNotaryKeys().toTypedArray()
        )

        val contextWithoutPlugin = sortedMapOf(
            NAME to notaryService.toString(),
            *convertNotaryKeys().toTypedArray()
        )

        val contextWithoutKeys = sortedMapOf(
            NAME to notaryService.toString(),
            PLUGIN to NOTARY_PLUGIN
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
            it.assertThat(result.pluginClass).isEqualTo(NOTARY_PLUGIN)
            it.assertThat(result.publicKey).isEqualTo(compositeKeyForNonEmptyKeys)
        }
    }

    @Test
    fun `converter converts notary service info successfully even if the notary keys are empty`() {
        val result = convertToNotaryInfo(contextWithoutKeys)
        assertSoftly {
            it.assertThat(result.name).isEqualTo(notaryService)
            it.assertThat(result.pluginClass).isEqualTo(NOTARY_PLUGIN)
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
    fun `exception is thrown when notary service's plugin type is missing`() {
        val ex = assertThrows<ValueNotFoundException> {
            convertToNotaryInfo(contextWithoutPlugin)
        }
        assertThat(ex.message).contains("plugin")
    }
}