package net.corda.libs.permissions.endpoints.v1.role.types

import net.corda.rest.exception.InvalidInputDataException
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class CreateRoleTypeTest {

    @Test
    fun testHappyPath() {
        assertDoesNotThrow {
            CreateRoleType(
                "cool-role",
                "awesome-group"
            )
        }
    }

    @Test
    fun testBlankRoleName() {
        Assertions.assertThatThrownBy {
            CreateRoleType(
                "",
                "awful-group"
            )
        }.hasMessage("Role name must not be null or blank.")
         .isInstanceOf(InvalidInputDataException::class.java)
    }
}