package net.corda.chunking.db.impl.validation

import net.corda.libs.cpiupload.ValidationException
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.core.CpiMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class ValidationFunctionsTest {
    @Nested
    inner class ValidateAndGetGroupPolicyFileVersionTest {
        @Test
        fun `it returns the version correctly`() {
            val groupPolicyString = "{\"fileFormatVersion\": 55}"
            val cpiMetadata = mock<CpiMetadata> {
                on { groupPolicy } doReturn groupPolicyString
            }
            val cpi = mock<Cpi> {
                on { metadata } doReturn cpiMetadata
            }

            assertThat(cpi.validateAndGetGroupPolicyFileVersion()).isEqualTo(55)
        }

        @Test
        fun `it throws exception if group policy is empty`() {
            val groupPolicyString = ""
            val cpiMetadata = mock<CpiMetadata> {
                on { groupPolicy } doReturn groupPolicyString
            }
            val cpi = mock<Cpi> {
                on { metadata } doReturn cpiMetadata
            }

            assertThrows<ValidationException> {
                cpi.validateAndGetGroupPolicyFileVersion()
            }
        }

        @Test
        fun `it throws exception if group policy is not JSON`() {
            val groupPolicyString = "1"
            val cpiMetadata = mock<CpiMetadata> {
                on { groupPolicy } doReturn groupPolicyString
            }
            val cpi = mock<Cpi> {
                on { metadata } doReturn cpiMetadata
            }

            assertThrows<ValidationException> {
                cpi.validateAndGetGroupPolicyFileVersion()
            }
        }

        @Test
        fun `it throws exception if group policy has no version`() {
            val groupPolicyString = "{}"
            val cpiMetadata = mock<CpiMetadata> {
                on { groupPolicy } doReturn groupPolicyString
            }
            val cpi = mock<Cpi> {
                on { metadata } doReturn cpiMetadata
            }

            assertThrows<ValidationException> {
                cpi.validateAndGetGroupPolicyFileVersion()
            }
        }
    }
}
