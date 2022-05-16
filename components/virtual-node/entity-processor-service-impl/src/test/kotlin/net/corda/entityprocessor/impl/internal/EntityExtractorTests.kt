package net.corda.entityprocessor.impl.internal

import net.corda.libs.packaging.core.CpkMetadata
import net.corda.libs.packaging.core.CordappManifest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

internal class EntityExtractorTests {
    @Test
    fun toClasses() {
        val classNames1 = setOf("net.corda.first", "net.corda.second")

        val manifest1 = mock<CordappManifest> {
            on { entities } doReturn classNames1
        }
        val metaData1 = mock<CpkMetadata> {
            on { cordappManifest } doReturn manifest1
        }

        val classNames2 = setOf("net.corda.third", "net.corda.fourth")

        val manifest2 = mock<CordappManifest> {
            on { entities } doReturn classNames2
        }
        val metaData2 = mock<CpkMetadata> {
            on { cordappManifest } doReturn manifest2
        }

        val classNames = EntityExtractor.getEntityClassNames(setOf(metaData1, metaData2))
        assertThat(classNames.size).isEqualTo(classNames1.size + classNames2.size)
        assertThat(classNames).isEqualTo(classNames1 + classNames2)
    }
}
