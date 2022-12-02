package net.corda.crypto.softhsm.impl

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.persistence.WrappingKeyStore
import net.corda.crypto.softhsm.WRAPPING_KEY_ENCODING_VERSION
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TransientSoftWrappingKeyMapTests {
    companion object {
        private lateinit var schemeMetadata: _root_ide_package_.net.corda.crypto.cipher.suite.CipherSchemeMetadata

        @JvmStatic
        @BeforeAll
        fun setup() {
            schemeMetadata = CipherSchemeMetadataImpl()
        }
    }

    @Test
    fun `getWrappingKey should return wrapping key`() {
        val master = WrappingKey.generateWrappingKey(schemeMetadata)
        val expected1 = WrappingKey.generateWrappingKey(schemeMetadata)
        val expected2 = WrappingKey.generateWrappingKey(schemeMetadata)
        val alias1 = "master-alias-1"
        val alias2 = "master-alias-2"
        val info1 = WrappingKeyInfo(WRAPPING_KEY_ENCODING_VERSION, expected1.algorithm, master.wrap(expected1))
        val info2 = WrappingKeyInfo(WRAPPING_KEY_ENCODING_VERSION, expected2.algorithm, master.wrap(expected2))
        val store = mock<WrappingKeyStore> {
            on { findWrappingKey(alias1) } doReturn info1
            on { findWrappingKey(alias2) } doReturn info2
        }
        val cut = TransientSoftWrappingKeyMap(store, master)
        val key11 = cut.getWrappingKey(alias1)
        assertEquals(expected1, key11)
        val key21 = cut.getWrappingKey(alias2)
        assertEquals(expected2, key21)
        assertNotEquals(key11, key21)

        val key12 = cut.getWrappingKey(alias1)
        assertEquals(expected1, key12)
        val key22 = cut.getWrappingKey(alias2)
        assertEquals(expected2, key22)

        Mockito.verify(store, times(4)).findWrappingKey(any())
    }

    @Test
    fun `getWrappingKey should throw IllegalArgumentException when encoding version is not recognised`() {
        val master = WrappingKey.generateWrappingKey(schemeMetadata)
        val expected = WrappingKey.generateWrappingKey(schemeMetadata)
        val alias = "master-alias-1"
        val info = WrappingKeyInfo(
            WRAPPING_KEY_ENCODING_VERSION + 1,
            expected.algorithm,
            master.wrap(expected)
        )
        val store = mock<WrappingKeyStore> {
            on { findWrappingKey(alias) } doReturn info
        }
        val cut = TransientSoftWrappingKeyMap(store, master)
        assertThrows<IllegalArgumentException> {
            cut.getWrappingKey(alias)
        }
    }

    @Test
    fun `getWrappingKey should throw IllegalArgumentException when key algorithm does not match master key`() {
        val master = WrappingKey.generateWrappingKey(schemeMetadata)
        val expected = WrappingKey.generateWrappingKey(schemeMetadata)
        val alias = "master-alias-1"
        val info = WrappingKeyInfo(
            WRAPPING_KEY_ENCODING_VERSION,
            expected.algorithm + "!",
            master.wrap(expected)
        )
        val store = mock<WrappingKeyStore> {
            on { findWrappingKey(alias) } doReturn info
        }
        val cut = TransientSoftWrappingKeyMap(store, master)
        assertThrows<IllegalArgumentException> {
            cut.getWrappingKey(alias)
        }
    }

    @Test
    fun `getWrappingKey should throw IllegalStateException when wrapping key is not found`() {
        val master = WrappingKey.generateWrappingKey(schemeMetadata)
        val alias = "master-alias-1"
        val store = mock<WrappingKeyStore> {
            on { findWrappingKey(alias) } doReturn null
        }
        val cut = TransientSoftWrappingKeyMap(store, master)
        assertThrows<IllegalStateException> {
            cut.getWrappingKey(alias)
        }
    }

    @Test
    fun `putWrappingKey should put wrapping key`() {
        val master = WrappingKey.generateWrappingKey(schemeMetadata)
        val expected = WrappingKey.generateWrappingKey(schemeMetadata)
        val alias = "master-alias"
        val info = WrappingKeyInfo(WRAPPING_KEY_ENCODING_VERSION, expected.algorithm, master.wrap(expected))
        val store = mock<WrappingKeyStore> {
            on { findWrappingKey(alias) } doReturn info
        }
        val cut = TransientSoftWrappingKeyMap(store, master)
        cut.putWrappingKey(alias, expected)
        val key = cut.getWrappingKey(alias)
        assertEquals(expected, key)
        Mockito.verify(store, times(1)).saveWrappingKey(any(), any())
        Mockito.verify(store, times(1)).findWrappingKey(alias)
    }

    @Test
    fun `exists should return true whenever key exist and false otherwise`() {
        val master = WrappingKey.generateWrappingKey(CipherSchemeMetadataImpl())
        val expected1 = WrappingKey.generateWrappingKey(CipherSchemeMetadataImpl())
        val expected2 = WrappingKey.generateWrappingKey(CipherSchemeMetadataImpl())
        val alias1 = "master-alias-1"
        val alias2 = "master-alias-2"
        val alias3 = "master-alias-3"
        val info1 = WrappingKeyInfo(WRAPPING_KEY_ENCODING_VERSION, expected1.algorithm, master.wrap(expected1))
        val info2 = WrappingKeyInfo(WRAPPING_KEY_ENCODING_VERSION, expected2.algorithm, master.wrap(expected2))
        val store = mock<WrappingKeyStore> {
            on { findWrappingKey(alias1) } doReturn info1
            on { findWrappingKey(alias2) } doReturn info2
            on { findWrappingKey(alias3) } doReturn null
        }
        val cut = TransientSoftWrappingKeyMap(store, master)
        cut.putWrappingKey(alias1, expected1)

        assertTrue(cut.exists(alias1))
        assertTrue(cut.exists(alias2))
        assertFalse(cut.exists(alias3))
    }
}