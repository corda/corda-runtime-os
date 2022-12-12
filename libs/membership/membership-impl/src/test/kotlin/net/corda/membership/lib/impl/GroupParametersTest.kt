package net.corda.membership.lib.impl

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.core.CompositeKeyProvider
import net.corda.crypto.impl.converter.PublicKeyConverter
import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.membership.lib.EPOCH_KEY
import net.corda.membership.lib.MODIFIED_TIME_KEY
import net.corda.membership.lib.MPV_KEY
import net.corda.membership.lib.impl.converter.NotaryInfoConverter
import net.corda.test.util.time.TestClock
import net.corda.v5.base.exceptions.ValueNotFoundException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.CompositeKeyNodeAndWeight
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.security.PublicKey
import java.time.Instant
import kotlin.test.assertFailsWith

class GroupParametersTest {
    private companion object {
        val clock = TestClock(Instant.ofEpochSecond(100))
        val modifiedTime = clock.instant()
        const val VALID_VALUE = 1
        const val INVALID_VALUE = 0
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

    private fun createTestParams(
        mpv: Int = VALID_VALUE,
        epoch: Int = VALID_VALUE,
        time: Instant = modifiedTime
    ) = LayeredPropertyMapMocks.create<GroupParametersImpl>(
        sortedMapOf(
            MPV_KEY to mpv.toString(),
            EPOCH_KEY to epoch.toString(),
            MODIFIED_TIME_KEY to time.toString(),
            "corda.notary.service.0.name" to notaryName.toString(),
            "corda.notary.service.0.plugin" to PLUGIN,
            "corda.notary.service.0.keys.0" to KEY
        ),
        listOf(NotaryInfoConverter(compositeKeyProvider), PublicKeyConverter(keyEncodingService))
    )

    @Test
    fun `group parameters are created successfully`() {
        val params = createTestParams()
        assertSoftly {
            it.assertThat(params.minimumPlatformVersion).isEqualTo(VALID_VALUE)
            it.assertThat(params.epoch).isEqualTo(VALID_VALUE)
            it.assertThat(params.modifiedTime).isEqualTo(modifiedTime)
            it.assertThat(params.notaries).hasSize(1)
            val notary = params.notaries.single()
            it.assertThat(notary.name).isEqualTo(notaryName)
            it.assertThat(notary.pluginClass).isEqualTo(PLUGIN)
            it.assertThat(notary.publicKey).isEqualTo(compositeKey)
        }
    }

    @Test
    fun `exception is thrown when MPV has invalid value`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            createTestParams(mpv = INVALID_VALUE)
        }
        assertThat(ex.message).isEqualTo("Platform version must be at least 1.")
    }

    @Test
    fun `exception is thrown when epoch has invalid value`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            createTestParams(epoch = INVALID_VALUE)
        }
        assertThat(ex.message).isEqualTo("Epoch must be at least 1.")
    }

    @Test
    fun `exception is thrown when modified time is missing`() {
        val params = LayeredPropertyMapMocks.create<GroupParametersImpl>(
            sortedMapOf(
                MPV_KEY to VALID_VALUE.toString(),
                EPOCH_KEY to VALID_VALUE.toString()
            ),
            emptyList()
        )

        val ex = assertFailsWith<ValueNotFoundException> {
            params.modifiedTime
        }
        assertThat(ex.message).contains(MODIFIED_TIME_KEY)
    }

    @Test
    fun `exception is thrown when epoch is missing`() {
        val ex = assertFailsWith<ValueNotFoundException> {
            LayeredPropertyMapMocks.create<GroupParametersImpl>(
                sortedMapOf(
                    MPV_KEY to VALID_VALUE.toString(),
                    MODIFIED_TIME_KEY to modifiedTime.toString()
                ),
                emptyList()
            )
        }
        assertThat(ex.message).contains(EPOCH_KEY)
    }
}