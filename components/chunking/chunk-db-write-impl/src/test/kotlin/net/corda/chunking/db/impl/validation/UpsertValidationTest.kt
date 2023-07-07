package net.corda.chunking.db.impl.validation

import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import net.corda.chunking.db.impl.persistence.database.DatabaseCpiPersistence
import net.corda.crypto.core.SecureHashImpl
import net.corda.libs.cpi.datamodel.repository.CpiMetadataRepository
import net.corda.libs.cpiupload.DuplicateCpiUploadException
import net.corda.libs.cpiupload.ValidationException
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.membership.lib.grouppolicy.GroupPolicyParser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock

class UpsertValidationTest {

    private companion object {
        fun createMockEntityManagerFactory(): EntityManagerFactory {
            val mockEntityManager = mock<EntityManager>() {
                on { it.transaction } doReturn (mock())
            }

            return mock() {
                on { it.createEntityManager() } doReturn (mockEntityManager)
            }
        }

        fun createCpiMetadataRepo(cpiId: CpiIdentifier): CpiMetadataRepository {
            val cpiMetadataMock = mock<CpiMetadata> {
                on { it.cpiId }.doReturn(cpiId)
                on { it.groupPolicy }.doReturn("{}")
            }

            val cpiMetadataRepoMock = mock<CpiMetadataRepository> {
                on {
                    findByNameAndSignerSummaryHash(
                        any(),
                        eq(cpiId.name),
                        eq(cpiId.signerSummaryHash)
                    )
                } doReturn (listOf(
                    cpiMetadataMock
                ))
            }

            return cpiMetadataRepoMock
        }

        fun createGroupPolicyParser(groupId: String) =
            mock<GroupPolicyParser.Companion> {
                on { groupIdFromJson(any()) } doReturn (groupId)
            }
    }

    @Test
    fun `succeeds with unique cpi`() {
        val cpiId = CpiIdentifier("aaa", "1.0", SecureHashImpl("SHA-256", "1234567890".toByteArray()))
        val groupId = "ABC"
        val groupPolicyParser = createGroupPolicyParser(groupId)

        val p = DatabaseCpiPersistence(createMockEntityManagerFactory(), mock(), mock(), groupPolicyParser)

        assertDoesNotThrow {
            p.validateCanUpsertCpi(cpiId, groupId, false, "id")
        }
    }

    @Test
    fun `succeeds force upload when version exact`() {
        val cpiId = CpiIdentifier("aaa", "1.0", SecureHashImpl("SHA-256", "1234567890".toByteArray()))
        val groupId = "ABC"
        val groupPolicyParser = createGroupPolicyParser(groupId)

        val p =
            DatabaseCpiPersistence(createMockEntityManagerFactory(), mock(), createCpiMetadataRepo(cpiId), groupPolicyParser)

        assertDoesNotThrow {
            p.validateCanUpsertCpi(cpiId, groupId, true, "id")
        }
    }

    @Test
    fun `fails force upload when version correct but groupId different to previous`() {
        val cpiId = CpiIdentifier("aaa", "1.0", SecureHashImpl("SHA-256", "1234567890".toByteArray()))
        val groupId = "ABC"
        val groupPolicyParser = createGroupPolicyParser("foo")

        val p =
            DatabaseCpiPersistence(createMockEntityManagerFactory(), mock(), createCpiMetadataRepo(cpiId), groupPolicyParser)

        assertThrows<ValidationException> {
            p.validateCanUpsertCpi(cpiId, groupId, true, "id")
        }
    }

    @Test
    fun `fails force upload when version is different`() {
        val cpiId = CpiIdentifier("aaa", "1.0", SecureHashImpl("SHA-256", "1234567890".toByteArray()))
        val groupId = "ABC"
        val groupPolicyParser = createGroupPolicyParser(groupId)

        val p =
            DatabaseCpiPersistence(
                createMockEntityManagerFactory(),
                mock(),
                createCpiMetadataRepo(cpiId.copy(version = "2.0")),
                groupPolicyParser
            )

        assertThrows<ValidationException> {
            p.validateCanUpsertCpi(cpiId, groupId, true, "id")
        }
    }

    @Test
    fun `succeeds upload when version different`() {
        val cpiId = CpiIdentifier("aaa", "1.0", SecureHashImpl("SHA-256", "1234567890".toByteArray()))
        val groupId = "ABC"
        val groupPolicyParser = createGroupPolicyParser(groupId)

        val p =
            DatabaseCpiPersistence(
                createMockEntityManagerFactory(),
                mock(),
                createCpiMetadataRepo(cpiId.copy(version = "2.0")),
                groupPolicyParser
            )

        assertDoesNotThrow {
            p.validateCanUpsertCpi(cpiId, groupId, false, "id")
        }
    }

    @Test
    fun `fails upload when version is same`() {
        val cpiId = CpiIdentifier("aaa", "1.0", SecureHashImpl("SHA-256", "1234567890".toByteArray()))
        val groupId = "ABC"
        val groupPolicyParser = createGroupPolicyParser(groupId)

        val p =
            DatabaseCpiPersistence(createMockEntityManagerFactory(), mock(), createCpiMetadataRepo(cpiId), groupPolicyParser)

        assertThrows<DuplicateCpiUploadException> {
            p.validateCanUpsertCpi(cpiId, groupId, false, "id")
        }
    }
}
