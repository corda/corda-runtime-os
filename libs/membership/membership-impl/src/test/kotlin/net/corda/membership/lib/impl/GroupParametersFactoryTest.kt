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
    }

    private val groupParametersCaptor = argumentCaptor<Map<String, String>>()
    private val mockLayeredPropertyMap: LayeredPropertyMap = mock()
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory = mock {
        on { createMap(groupParametersCaptor.capture()) } doReturn mockLayeredPropertyMap
    }

    private val groupParametersFactory = GroupParametersFactoryImpl(layeredPropertyMapFactory)

    @Test
    fun `creating GroupParameters`() {
        val entries =  KeyValuePairList(listOf(
            KeyValuePair(EPOCH_KEY, "1"),
            KeyValuePair(MPV_KEY, MPV),
            KeyValuePair(MODIFIED_TIME_KEY, Instant.now().toString())
        ))
        groupParametersFactory.create(entries)

        assertThat(groupParametersCaptor.firstValue).isEqualTo(entries.toMap())
    }
}
