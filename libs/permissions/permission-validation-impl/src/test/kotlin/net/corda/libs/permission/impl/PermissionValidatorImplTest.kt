package net.corda.libs.permission.impl

import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.corda.data.permissions.PermissionType
import net.corda.data.permissions.summary.PermissionSummary
import net.corda.data.permissions.summary.UserPermissionSummary
import net.corda.libs.permissions.validation.cache.PermissionValidationCache
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PermissionValidatorImplTest {

    private val permissionValidationCache: PermissionValidationCache = mock()
    private val permissionValidator = PermissionValidatorImpl(permissionValidationCache)
    private val permissionString = "flow/start/com.myapp.MyFlow"
    private val permissionUrlRequest = "POST:https://host:1234/node-rpc/5e0a07a6-c25d-413a-be34-647a792f4f58/${permissionString}"

    private val userPermissionSummary = UserPermissionSummary(
        "userLoginName",
        true,
        listOf(
            PermissionSummary(
                "id1", null, null, "POST:.*$permissionString",
                PermissionType.ALLOW
            )
        ),
        Instant.now()
    )

    @Test
    fun `authorize user will return false when user summary cannot be found in cache`() {
        whenever(permissionValidationCache.getPermissionSummary("userLoginName1")).thenReturn(null)

        val result = permissionValidator.authorizeUser("userLoginName1", permissionUrlRequest)

        assertFalse(result)
    }

    @Test
    fun `User with proper permission will be authorized`() {

        whenever(permissionValidationCache.getPermissionSummary("userLoginName")).thenReturn(userPermissionSummary)

        assertTrue(permissionValidator.authorizeUser("userLoginName", permissionUrlRequest))
    }

    @Test
    fun `User with proper permission set to DENY will not be authorized`() {

        val userPermissionSummary = UserPermissionSummary(
            "userLoginName",
            true,
            listOf(
                PermissionSummary(
                    "id2", null, null, "POST:.*$permissionString",
                    PermissionType.DENY
                )
            ),
            Instant.now()
        )
        whenever(permissionValidationCache.getPermissionSummary("userLoginName")).thenReturn(userPermissionSummary)

        assertFalse(permissionValidator.authorizeUser("userLoginName", permissionUrlRequest))
    }
}