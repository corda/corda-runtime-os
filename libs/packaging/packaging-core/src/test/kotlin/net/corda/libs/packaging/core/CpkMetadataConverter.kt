package net.corda.libs.packaging.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CpkMetadataConverter {
    @Test
    fun `can convert to json avro and back`() {
        val cpkMetadata = CpkMetaTestData.create()

        val json = cpkMetadata.toJsonAvro()
        val deserialised = CpkMetadata.fromJsonAvro(json)

        assertThat(deserialised).isEqualTo(cpkMetadata)
    }
}