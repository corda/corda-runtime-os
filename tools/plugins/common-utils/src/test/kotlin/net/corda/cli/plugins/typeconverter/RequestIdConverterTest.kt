package net.corda.cli.plugins.typeconverter

import net.corda.cli.plugins.data.RequestId
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

class RequestIdConverterTest {

    @Test
    fun `converts request id string to request id`() {
        val requestIdConverter = RequestIdConverter()
        val requestIdString = "abcdefg-hijklmn-opqrst-uvwxyz-123456-7890ab"

        val requestId = requestIdConverter.convert(requestIdString)
        assertThat(RequestId(requestIdString)).isEqualTo(requestId)
    }
}
