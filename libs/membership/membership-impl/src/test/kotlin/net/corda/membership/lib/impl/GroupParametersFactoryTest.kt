package net.corda.membership.lib.impl

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.membership.lib.EPOCH_KEY
import net.corda.membership.lib.MODIFIED_TIME_KEY
import net.corda.membership.lib.MPV_KEY
import net.corda.membership.lib.toMap
import net.corda.v5.base.types.LayeredPropertyMap
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.Instant

class GroupParametersFactoryTest {
    private companion object {
        const val MPV = "5000"
        const val EPOCH = "1"
    }

    private val groupParametersCaptor = argumentCaptor<Map<String, String>>()
    private val mockLayeredPropertyMap: LayeredPropertyMap = mock {
        on { it.parse(EPOCH_KEY, Int::class.java) } doReturn EPOCH.toInt()
        on { it.parse(MPV_KEY, Int::class.java) } doReturn MPV.toInt()
        on { it.parse(MODIFIED_TIME_KEY, Instant::class.java) } doReturn Instant.now()
    }
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory = mock {
        on { createMap(groupParametersCaptor.capture()) } doReturn mockLayeredPropertyMap
    }

    private val groupParametersFactory = GroupParametersFactoryImpl(layeredPropertyMapFactory)

    @Test
    fun `factory creating GroupParameters`() {
        val entries =  KeyValuePairList(listOf(
            KeyValuePair(EPOCH_KEY, EPOCH),
            KeyValuePair(MPV_KEY, MPV),
            KeyValuePair(MODIFIED_TIME_KEY, Instant.now().toString())
        ))

        groupParametersFactory.create(entries)

        assertThat(groupParametersCaptor.firstValue).isEqualTo(entries.toMap())
    }

    @Test
    fun `factory successfully creates and returns GroupParameters`() {
        val entries =  KeyValuePairList(listOf(
            KeyValuePair(EPOCH_KEY, EPOCH),
            KeyValuePair(MPV_KEY, MPV),
            KeyValuePair(MODIFIED_TIME_KEY, Instant.now().toString())
        ))

        val groupParameters = groupParametersFactory.create(entries)

        with(groupParameters) {
            assertThat(epoch).isEqualTo(EPOCH.toInt())
            assertThat(minimumPlatformVersion).isEqualTo(MPV.toInt())
            assertThat(modifiedTime).isBeforeOrEqualTo(Instant.now())
        }
    }
}
