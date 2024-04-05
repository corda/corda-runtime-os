package net.corda.cli.plugins.typeconverter

import net.corda.crypto.core.ShortHash
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

class ShotHashConverterTest {

    @Test
    fun `converts short hash string to short hash`() {
        val shortHashConverter = ShortHashConverter()
        val shortHashString = "1234567890ab" // should be at least 12 characters long

        val shortHash = shortHashConverter.convert(shortHashString)
        assertThat(ShortHash.of(shortHashString)).isEqualTo(shortHash)
    }
}
