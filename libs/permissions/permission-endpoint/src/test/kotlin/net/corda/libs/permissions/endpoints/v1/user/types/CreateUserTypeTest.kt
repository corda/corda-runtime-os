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
            CreateUserType(
                "Andrew O'Malia", "Andrew.OMalia@company.com", true, "secret'1234!", Instant.now(),
                UUID.randomUUID().toString()
            )
        }
    }

    @Test
    fun testTooLong() {
        Assertions.assertThatThrownBy {
            CreateUserType("abc".repeat(100), "abc".repeat(100), true, "abc".repeat(100), null,
                "1234")
        }.hasMessage("Invalid input data for user creation.").matches {
            (it as InvalidInputDataException).details == mapOf(
                "Error #1" to "Full name exceed maximum length of 255.",
                "Error #2" to "Login name exceed maximum length of 255.",
                "Error #3" to "Login name contains invalid characters. Correct pattern is: '[-._@a-zA-Z0-9]{3,255}'.",
                "Error #4" to "Password exceed maximum length of 255.",
                "Error #5" to "Invalid UUID string: 1234"
            )
        }
    }

    @Test
    fun testTooShort() {
        Assertions.assertThatThrownBy {
            CreateUserType("abc", "abc", true, "abc", null,
                null)
        }.hasMessage("Invalid input data for user creation.").matches {
            (it as InvalidInputDataException).details == mapOf(
                "Error #1" to "Password is too short. Minimum length is 5."
            )
        }
    }

    @Test
    fun testBlankFullName() {
        Assertions.assertThatThrownBy {
            CreateUserType(
                "", "Joe.Bloggs@company.com", true, "secret1234!", Instant.now(),
                UUID.randomUUID().toString())
        }.hasMessage("Invalid input data for user creation.").matches {
            (it as InvalidInputDataException).details == mapOf(
                "Error #1" to "Full name must not be blank.",
            )
        }
    }

    @Test
    fun testBlankLoginName() {
        Assertions.assertThatThrownBy {
            CreateUserType(
                "Joe Bloggs", "", true, "secret1234!", Instant.now(),
                UUID.randomUUID().toString())
        }.hasMessage("Invalid input data for user creation.").matches {
            (it as InvalidInputDataException).details == mapOf(
                "Error #1" to "Login name must not be blank.",
                "Error #2" to "Login name contains invalid characters. Correct pattern is: '[-._@a-zA-Z0-9]{3,255}'.",
            )
        }
    }

    @Test
    fun testWrongChars() {
        Assertions.assertThatThrownBy {
            CreateUserType("abc{}", "def()", true, "passw+=", null,
                "1234")
        }.hasMessage("Invalid input data for user creation.").matches {
            (it as InvalidInputDataException).details == mapOf(
                "Error #1" to "Full name contains invalid characters. Allowed characters are: 'a-zA-Z0-9.@\\-#\' '.",
                "Error #2" to "Login name contains invalid characters. Correct pattern is: '[-._@a-zA-Z0-9]{3,255}'.",
                "Error #3" to "Invalid UUID string: 1234"
            )
        }
    }
}