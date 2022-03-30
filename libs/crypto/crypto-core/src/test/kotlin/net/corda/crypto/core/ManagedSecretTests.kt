package net.corda.crypto.core

import net.corda.crypto.core.ManagedSecret.Companion.SECRET_MINIMAL_LENGTH
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class ManagedSecretTests {
    @Test
    fun `Should always derive same secret for the same passphrase and salt`() {
        val passphrase = UUID.randomUUID().toString()
        val salt = UUID.randomUUID().toString()
        val secret1 = ManagedSecret.derive(passphrase, salt)
        val secret2 = ManagedSecret.derive(passphrase, salt)
        assertArrayEquals(secret1.secret, secret2.secret)
    }

    @Test
    fun `Should derive different secrets for different passphrase`() {
        val salt = UUID.randomUUID().toString()
        val secret1 = ManagedSecret.derive(UUID.randomUUID().toString(), salt)
        val secret2 = ManagedSecret.derive(UUID.randomUUID().toString(), salt)
        assertFalse(secret1.secret.contentEquals(secret2.secret))
    }

    @Test
    fun `Should derive different secrets for different salt`() {
        val passphrase = UUID.randomUUID().toString()
        val secret1 = ManagedSecret.derive(passphrase, UUID.randomUUID().toString())
        val secret2 = ManagedSecret.derive(passphrase, UUID.randomUUID().toString())
        assertFalse(secret1.secret.contentEquals(secret2.secret))
    }

    @Test
    fun `Should derive different secrets for different passphrase and salt`() {
        val secret1 = ManagedSecret.derive(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val secret2 = ManagedSecret.derive(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        assertFalse(secret1.secret.contentEquals(secret2.secret))
    }

    @Test
    fun `Should fail to derive for blank passphrase`() {
        assertThrows<IllegalArgumentException> {
            ManagedSecret.derive("", UUID.randomUUID().toString())
        }
    }

    @Test
    fun `Should fail to derive for blank salt`() {
        assertThrows<IllegalArgumentException> {
            ManagedSecret.derive(UUID.randomUUID().toString(), "")
        }
    }

    @Test
    fun `Should fail to derive for size less than defined limit`() {
        (0 until SECRET_MINIMAL_LENGTH).forEach {
            assertThrows<IllegalArgumentException> {
                ManagedSecret.derive(UUID.randomUUID().toString(), UUID.randomUUID().toString(), it, 536)
            }
        }
    }

    @Test
    fun `Should derive for size of defined limit`() {
        val secret = ManagedSecret.derive(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            SECRET_MINIMAL_LENGTH,
            536
        )
        assertEquals(SECRET_MINIMAL_LENGTH, secret.secret.size)
    }

    @Test
    @Suppress("ForEachOnRange")
    fun `Should derive for arbitrary size greater than the defined limit`() {
        (33..41).forEach {
            val secret = ManagedSecret.derive(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                it,
                536
            )
            assertEquals(it, secret.secret.size)
        }
    }

    @Test
    fun `Should generate different secrets`() {
        val secret1 = ManagedSecret.generate()
        val secret2 = ManagedSecret.generate()
        assertFalse(secret1.secret.contentEquals(secret2.secret))
    }

    @Test
    fun `Should fail to generate for size less than defined limit`() {
        (0 until SECRET_MINIMAL_LENGTH).forEach {
            assertThrows<IllegalArgumentException> {
                ManagedSecret.generate(it)
            }
        }
    }

    @Test
    fun `Should generate for size of defined limit`() {
        val secret = ManagedSecret.generate(SECRET_MINIMAL_LENGTH)
        assertEquals(SECRET_MINIMAL_LENGTH, secret.secret.size)
    }

    @Test
    @Suppress("ForEachOnRange")
    fun `Should generate for arbitrary size greater than the defined limit`() {
        (33..41).forEach {
            val secret = ManagedSecret.generate(it)
            assertEquals(it, secret.secret.size)
        }
    }
    @Test
    fun `Should return hash code of the secret key`() {
        val secret = ManagedSecret.generate()
        assertEquals(secret.secret.hashCode(), secret.hashCode())
    }

    @Test
    fun `Should be equal for the same secret key`() {
        val base = ManagedSecret.generate()
        val secret1 = ManagedSecret(base.secret)
        val secret2 = ManagedSecret(base.secret)
        assertEquals(secret1, secret2)
    }

    @Test
    fun `Should be equal to itself`() {
        val secret = ManagedSecret.generate()
        assertEquals(secret, secret)
    }

    @Test
    fun `Should not be equal for the different secret keys`() {
        val secret1 = ManagedSecret.generate()
        val secret2 = ManagedSecret.generate()
        assertNotEquals(secret1, secret2)
    }

    @Test
    fun `Should be equal to object of different type`() {
        val secret = ManagedSecret.generate()
        assertFalse(secret.equals("Hello World!"))
    }
}