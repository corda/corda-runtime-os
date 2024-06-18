package net.corda.libs.permission.impl

import net.corda.data.permissions.PermissionType
import net.corda.data.permissions.summary.PermissionSummary
import net.corda.data.permissions.summary.UserPermissionSummary
import net.corda.libs.permissions.validation.cache.PermissionValidationCache
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PermissionValidatorImplTest {

    private val cpiUploadRequest = "POST:/api/v5_3/cpi"
    private val certsInstallRequest = "PUT:/api/v5_3/certificate/p2p-tls/cluster"

    private val userName = "userLoginName"

    private val userPermissionSummaries = UserPermissionSummary(
        userName,
        true,
        listOf(
            PermissionSummary(
                "id1",
                null,
                null,
                cpiUploadRequest,
                PermissionType.ALLOW
            ),
            PermissionSummary(
                "id2",
                null,
                null,
                certsInstallRequest,
                PermissionType.DENY
            )
        ),
        Instant.now()
    )

    private val permissionValidationCache: PermissionValidationCache = mock<PermissionValidationCache>().apply {
        whenever(getPermissionSummary(userName)).thenReturn(userPermissionSummaries)
    }

    private val permissionValidator = PermissionValidatorImpl(AtomicReference(permissionValidationCache))

    @Test
    fun `authorize user will return false when user summary cannot be found in cache`() {
        assertFalse(permissionValidator.authorizeUser("differentUser", cpiUploadRequest))
    }

    @Test
    fun `will return false for missing permission`() {
        assertFalse(permissionValidator.authorizeUser(userName, "GET:/api/v5_3/mgm/12345678/info"))
    }

    @Test
    fun `User with proper permission will be authorized`() {
        assertTrue(permissionValidator.authorizeUser(userName, cpiUploadRequest))
    }

    @Test
    fun `User with proper permission set to DENY will not be authorized`() {
        assertFalse(permissionValidator.authorizeUser(userName, certsInstallRequest))
    }
}
