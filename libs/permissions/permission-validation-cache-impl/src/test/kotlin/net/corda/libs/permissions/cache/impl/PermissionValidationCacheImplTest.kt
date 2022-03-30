package net.corda.libs.permissions.cache.impl

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import net.corda.data.permissions.PermissionType
import net.corda.data.permissions.summary.PermissionSummary
import net.corda.data.permissions.summary.UserPermissionSummary
import net.corda.libs.permissions.cache.exception.PermissionCacheException
import net.corda.libs.permissions.validation.cache.impl.PermissionValidationCacheImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class PermissionValidationCacheImplTest {

    private val permissionSummaryData = ConcurrentHashMap<String, UserPermissionSummary>()
    private val permissionValidationCache = PermissionValidationCacheImpl(permissionSummaryData)

    private val permissionSummary1 = UserPermissionSummary(
        "userlogin1",
        true,
        listOf(PermissionSummary("id1", null, null, "", PermissionType.ALLOW)),
        Instant.now()
    )
    private val permissionSummary2 = UserPermissionSummary(
        "userlogin2",
        true,
        listOf(PermissionSummary("id2", null, null, "", PermissionType.DENY)),
        Instant.now()
    )

    @BeforeEach
    fun setUp() {
        permissionValidationCache.start()
        permissionSummaryData["userLogin1"] = permissionSummary1
        permissionSummaryData["userLogin2"] = permissionSummary2
    }

    @Test
    fun `stopped permission cache prevents calling user functions`() {
        permissionValidationCache.stop()
        assertThrows(PermissionCacheException::class.java) {
            permissionValidationCache.getPermissionSummary("id")
        }
        assertThrows(PermissionCacheException::class.java) {
            permissionValidationCache.permissionSummaries
        }
    }

    @Test
    fun getPermissionSummaries() {
        val permissionSummariesMap = permissionValidationCache.permissionSummaries
        val permissionIds = permissionSummariesMap.keys
        val permissions = permissionSummariesMap.values
        assertEquals(2, permissionSummariesMap.size,
            "GetPermissionSummaries should return all permission summaries in the map.")
        assertTrue(permissionIds.containsAll(listOf("userLogin1", "userLogin2")),
            "GetPermissions result should contain permission IDs as keys.")
        assertTrue(permissions.containsAll(listOf(permissionSummary1, permissionSummary2)),
            "GetPermissions result should contain expected permissions in the map.")
    }
}