package net.corda.crypto.service.impl

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.core.KeyAlreadyExistsException
import net.corda.crypto.persistence.HSMStore
import net.corda.crypto.core.SigningKeyInfo
import net.corda.crypto.softhsm.SigningRepository
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SigningServiceImplTest {
    private val repo = mock<SigningRepository>()
    private val schemeMetadata = mock<CipherSchemeMetadata>()

    private val masterKeyAlias = "IAMGROOT"

    private val association =  mock<HSMAssociationInfo> {
        on { masterKeyAlias }.thenReturn(masterKeyAlias)
    }
    private val mockHsmStore = mock<HSMStore> {
        on { findTenantAssociation(any(), any()) } doReturn association
    }

    private val service = SigningServiceImpl(
        cryptoService = mock(),
        signingRepositoryFactory = { repo },
        schemeMetadata = schemeMetadata,
        digestService = mock(),
        signingKeyInfoCache = mock(),
        hsmStore = mockHsmStore
    )

    @Test
    fun `generateKeyPair throws KeyAlreadyExistsException if the key already exists`() {
        val tenantId = "ID"
        val category = "category"
        val alias = "alias"
        val scheme = mock<KeyScheme>()
        val context = emptyMap<String, String>()
        val key = mock<SigningKeyInfo>()
        whenever(repo.findKey(alias)).doReturn(key)

        assertThrows<KeyAlreadyExistsException> {
            service.generateKeyPair(
                tenantId,
                category,
                alias,
                scheme,
                context
            )
        }
    }
}
