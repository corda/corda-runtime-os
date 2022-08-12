package net.corda.chunking.db.impl.validation

import net.corda.chunking.db.impl.persistence.CpiPersistence
import net.corda.libs.cpiupload.DuplicateCpiUploadException
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class ValidateUniqueCpiAndGroupIdTest {
    @Test
    fun `succeeds with unique groupId`() {
        val requiredName = "aaa"
        val requiredVersion = "1.0"
        val hash = SecureHash.create("DUMMY:1234567890")
        val mockCpiId = mock<CpiIdentifier> {
            on { name }.doReturn(requiredName)
            on { version }.doReturn(requiredVersion)
            on { signerSummaryHash }.doReturn(hash)
        }
        val mockMetadata = mock<CpiMetadata> { on { cpiId }.doReturn(mockCpiId) }
        val cpi = mock<Cpi> { on { metadata }.doReturn(mockMetadata) }

        // Return null to indicate this cpi does not exist
        val cpiDoesNotExist = mock<CpiPersistence> {
            on { getGroupId(requiredName, requiredVersion, hash.toString()) }.doReturn(null)
        }

        // doesn't throw
        cpiDoesNotExist.verifyGroupIdIsUniqueForCpi(cpi)
    }

    @Test
    fun `fails with existing groupId`() {
        val requiredName = "aaa"
        val requiredVersion = "1.0"
        val hash = SecureHash.create("DUMMY:1234567890")

        val groupId = "ABC"
        val mockCpiId = mock<CpiIdentifier> {
            on { name }.doReturn(requiredName)
            on { version }.doReturn(requiredVersion)
            on { signerSummaryHash }.doReturn(hash)
        }
        val mockMetadata = mock<CpiMetadata> { on { cpiId }.doReturn(mockCpiId) }
        val cpi = mock<Cpi> { on { metadata }.doReturn(mockMetadata) }

        // Return the group id to indicate this *does* exist.
        val cpiExists = mock<CpiPersistence> {
            on { getGroupId(requiredName, requiredVersion, hash.toString()) }.doReturn(groupId)
        }

        assertThrows<DuplicateCpiUploadException> { cpiExists.verifyGroupIdIsUniqueForCpi(cpi) }
    }
}
