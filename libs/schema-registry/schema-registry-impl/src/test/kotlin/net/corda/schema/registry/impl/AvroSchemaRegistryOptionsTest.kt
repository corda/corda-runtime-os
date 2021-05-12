package net.corda.schema.registry.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class AvroSchemaRegistryOptionsTest {
    @Test
    fun `correct flags from options`() {
        val compressedOptions = AvroSchemaRegistryImpl.Options(
            compressed = true
        )
        assertThat(compressedOptions.toFlags()).isEqualTo(1)
        val uncompressedOptions = AvroSchemaRegistryImpl.Options(
            compressed = false
        )
        assertThat(uncompressedOptions.toFlags()).isEqualTo(0)
    }

    @Test
    fun `correct options from flags`() {
        val compressedOptions = AvroSchemaRegistryImpl.Options.from(1)
        assertThat(compressedOptions.compressed).isTrue
        val uncompressedOptions = AvroSchemaRegistryImpl.Options.from(0)
        assertThat(uncompressedOptions.compressed).isFalse
    }

    @Test
    fun `assert no unexpected flags added`() {
        AvroSchemaRegistryImpl.Options.from(1.inv())
    }
}