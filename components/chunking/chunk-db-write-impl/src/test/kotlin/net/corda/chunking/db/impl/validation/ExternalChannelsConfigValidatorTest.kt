package net.corda.chunking.db.impl.validation

import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.CpkMetadata
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class ExternalChannelsConfigValidatorTest {

    private val externalChannelsConfigValidator = ExternalChannelsConfigValidatorImpl()

    @Test
    fun `throws exception when string is not null because the method is not implemented - non-empty string`() {
        val mockCpkMetadata = mock<CpkMetadata> { on { externalChannelsConfig }.doReturn( "{}") }
        val mockCpiMetadata = mock<CpiMetadata> { on { cpksMetadata }.doReturn(listOf(mockCpkMetadata) ) }
        val cpi = mock<Cpi> { on { metadata }.doReturn(mockCpiMetadata) }

        assertThrows<NotImplementedError> {
            externalChannelsConfigValidator.validate(cpi)
        }
    }

    @Test
    fun `throws exception when string is not null because the method is not implemented - empty string`() {
        val mockCpkMetadata = mock<CpkMetadata> { on { externalChannelsConfig }.doReturn( "") }
        val mockCpiMetadata = mock<CpiMetadata> { on { cpksMetadata }.doReturn(listOf(mockCpkMetadata) ) }
        val cpi = mock<Cpi> { on { metadata }.doReturn(mockCpiMetadata) }

        assertThrows<NotImplementedError> {
            externalChannelsConfigValidator.validate(cpi)
        }
    }

    @Test
    fun `does not throw exception when string is null`() {
        val mockCpkMetadata = mock<CpkMetadata> { on { externalChannelsConfig }.doReturn( null) }
        val mockCpiMetadata = mock<CpiMetadata> { on { cpksMetadata }.doReturn(listOf(mockCpkMetadata) ) }
        val cpi = mock<Cpi> { on { metadata }.doReturn(mockCpiMetadata) }

        assertDoesNotThrow {
            externalChannelsConfigValidator.validate(cpi)
        }
    }
}
