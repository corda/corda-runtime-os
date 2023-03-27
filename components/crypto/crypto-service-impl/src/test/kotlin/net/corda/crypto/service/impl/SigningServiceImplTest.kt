package net.corda.crypto.service.impl

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.core.KeyAlreadyExistsException
import net.corda.crypto.persistence.SigningKeyInfo
import net.corda.crypto.service.CryptoServiceFactory
import net.corda.crypto.softhsm.SigningRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SigningServiceImplTest {
    private val repo = mock<SigningRepository>()
    private val cryptoServiceFactory = mock<CryptoServiceFactory>()
    private val schemeMetadata = mock<CipherSchemeMetadata>()
    private val service = SigningServiceImpl(
        cryptoServiceFactory = cryptoServiceFactory,
        signingRepositoryFactory = { repo },
        schemeMetadata = schemeMetadata,
        digestService = mock(),
        cache = mock()
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
