package net.corda.crypto.service.impl

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.core.KeyAlreadyExistsException
import net.corda.crypto.persistence.SigningCachedKey
import net.corda.crypto.persistence.SigningKeyStore
import net.corda.crypto.service.CryptoServiceFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SigningServiceImplTest {
    private val store = mock<SigningKeyStore>()
    private val cryptoServiceFactory = mock<CryptoServiceFactory>()
    private val schemeMetadata = mock<CipherSchemeMetadata>()
    private val service = SigningServiceImpl(
        store,
        cryptoServiceFactory,
        schemeMetadata,
        mock()
    )

    @Test
    fun `generateKeyPair throws KeyAlreadyExistsException if the key already exists`() {
        val tenantId = "ID"
        val category = "category"
        val alias = "alias"
        val scheme = mock<KeyScheme>()
        val context = emptyMap<String, String>()
        val key = mock<SigningCachedKey>()
        whenever(store.find(tenantId, alias)).doReturn(key)

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
