package net.corda.libs.configuration.secret

import net.corda.crypto.core.Encryptor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class SecretEncryptionUtilTest {
    // encryptor unfortunately doesn't have an interface, so creating a real one, but only once.
    private val encryptor = Encryptor.derive("p", "s")
    private val encryptorFactoryMock = mock<(p: String, s: String) -> Encryptor>() {
        on { invoke(any(), any()) } doReturn (encryptor)
    }

    private val util = SecretEncryptionUtil(encryptorFactoryMock)

    @Test
    fun `when encrypt can decrypt again`() {
        val plain = "plain text"
        val encrypted = util.encrypt(plain, "salt", "pass")
        println("Encrypted: $encrypted")
        val decrypted = util.decrypt(encrypted, "salt", "pass")
        println("Decrypted: $decrypted")
        assertThat(decrypted).isEqualTo(plain)
    }

    @Test
    fun `when encrypting twice only create encryptor once`() {
        util.encrypt("hello", "salt", "pass")
        util.encrypt("fred", "salt", "pass")

        verify(encryptorFactoryMock, times(1)).invoke("pass", "salt")
    }

    @Test
    fun `encrypt and decrypt use the same encryptor`() {
        val encrypted = util.encrypt("hello", "salt", "pass")
        util.decrypt(encrypted, "salt", "pass")

        verify(encryptorFactoryMock, times(1)).invoke("pass", "salt")
    }

    @Test
    fun `when decrypt with invalid cyphertext throw`() {
        assertThrows<IllegalArgumentException> {
            util.decrypt("foo", "salt", "pass")
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `when encrypt and salt blank or empty throw`(salt: String) {
        assertThrows<IllegalArgumentException> {
            util.encrypt("foo", salt, "pass")
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `when encrypt and passphrase blank or empty throw`(passphrase: String) {
        assertThrows<IllegalArgumentException> {
            util.encrypt("foo", "salt", passphrase)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `when decrypt and salt blank or empty throw`(salt: String) {
        assertThrows<IllegalArgumentException> {
            util.decrypt("foo", salt, "pass")
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `when decrypt and passphrase blank or empty throw`(passphrase: String) {
        assertThrows<IllegalArgumentException> {
            util.decrypt("foo", "salt", passphrase)
        }
    }

}