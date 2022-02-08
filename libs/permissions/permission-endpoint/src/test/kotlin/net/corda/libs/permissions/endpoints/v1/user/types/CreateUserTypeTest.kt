package net.corda.libs.permissions.endpoints.v1.user.types

import net.corda.httprpc.exception.InvalidInputDataException
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.Instant
import java.util.*

class CreateUserTypeTest {

    @Test
    fun testSunnyDay() {
        // Should create successfully
        assertDoesNotThrow {
            CreateUserType(
                "Joe Bloggs", "Joe.Bloggs@company.com", true, "secret1234!", Instant.now(),
                UUID.randomUUID().toString()
            )
        }
    }

    @Test
    fun testTooLong() {
        Assertions.assertThatThrownBy {
            CreateUserType("abc".repeat(100), "abc".repeat(100), true, "abc".repeat(100), null,
                "1234")
        }.isInstanceOf(InvalidInputDataException::class.java).hasMessage(
            "Invalid input for user creation: Full name exceed maximum length of 255. " +
                    "Login name exceed maximum length of 255. Password name exceed maximum length of 255. " +
                    "Invalid UUID string: 1234")
    }

    @Test
    fun testWrongChars() {
        Assertions.assertThatThrownBy {
            CreateUserType("abc{}", "def()", true, "passw+=", null,
                "1234")
        }.isInstanceOf(InvalidInputDataException::class.java).hasMessage(
            "Invalid input for user creation: " +
                    "Full name 'abc{}' contains invalid characters. Allowed characters are: 'a-zA-Z0-9.@\\-# '. " +
                    "Login name 'def()' contains invalid characters. Allowed characters are: 'a-zA-Z0-9.@\\-#'. " +
                    "Password contains invalid characters. Allowed characters are: 'a-zA-Z0-9.@\\-#!?,'. " +
                    "Invalid UUID string: 1234")
    }
}