package net.corda.chunking.db.impl.validation

import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.CpkMetadata
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@Disabled("This test should be enabled once the schema is available")
class ExternalChannelsConfigValidatorTest {

    private val externalChannelsConfigValidator = ExternalChannelsConfigValidatorImpl()

    @Test
    fun `does not throw exception when the configuration is valid`() {
        val mockCpkMetadata = mock<CpkMetadata> { on { externalChannelsConfig }.doReturn( "{ }" ) }
        val mockCpiMetadata = mock<CpiMetadata> { on { cpksMetadata }.doReturn(listOf(mockCpkMetadata) ) }

        assertDoesNotThrow {
            externalChannelsConfigValidator.validate(mockCpiMetadata.cpksMetadata)
        }
    }

    @Test
    fun `does not throw exception when the configuration is null`() {
        val mockCpkMetadata = mock<CpkMetadata> { on { externalChannelsConfig }.doReturn( null ) }
        val mockCpiMetadata = mock<CpiMetadata> { on { cpksMetadata }.doReturn(listOf(mockCpkMetadata) ) }

        assertDoesNotThrow {
            externalChannelsConfigValidator.validate(mockCpiMetadata.cpksMetadata)
        }
    }

    @Test
    fun `throws exception when the configuration is invalid`() {
        val mockCpkMetadata = mock<CpkMetadata> { on { externalChannelsConfig }.doReturn( "invalid schema") }
        val mockCpiMetadata = mock<CpiMetadata> { on { cpksMetadata }.doReturn(listOf(mockCpkMetadata) ) }

        assertThrows<NotImplementedError> {
            externalChannelsConfigValidator.validate(mockCpiMetadata.cpksMetadata)
        }
    }

    @Test
    fun `throws exception when the configuration string is empty`() {
        val mockCpkMetadata = mock<CpkMetadata> { on { externalChannelsConfig }.doReturn( "" ) }
        val mockCpiMetadata = mock<CpiMetadata> { on { cpksMetadata }.doReturn(listOf(mockCpkMetadata) ) }

        assertThrows<NotImplementedError> {
            externalChannelsConfigValidator.validate(mockCpiMetadata.cpksMetadata)
        }
    }
}
