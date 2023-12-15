package net.corda.libs.permissions.endpoints.v1.permission.types

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class CreatePermissionTypeTest {

    @Test
    fun testHappyPath() {
        assertDoesNotThrow {
            CreatePermissionType(
                PermissionType.ALLOW,
                "permission-a-ok",
                "cool-group",
                "neat-node-1"
            )
        }
    }

    @Test
    fun testBlankPermissionString() {
        Assertions.assertThatThrownBy {
            CreatePermissionType(
                PermissionType.ALLOW,
                "",
                "uncool-group",
                "not-so-neat-node-1"
            )
        }.hasMessage("Permission string must not be null or blank.")
    }
}
