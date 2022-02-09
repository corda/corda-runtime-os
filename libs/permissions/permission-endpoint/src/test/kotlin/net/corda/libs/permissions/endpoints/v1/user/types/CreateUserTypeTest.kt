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
                "Error #3" to "Password name exceed maximum length of 255.",
                "Error #4" to "Invalid UUID string: 1234"
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
                "Error #2" to "Login name contains invalid characters. Allowed characters are: 'a-zA-Z0-9.@\\-#'.",
                "Error #3" to "Invalid UUID string: 1234"
            )
        }
    }
}