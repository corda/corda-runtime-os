package net.corda.libs.permissions.storage.reader.impl.summary

import java.time.Instant
import java.time.temporal.ChronoUnit
import net.corda.data.permissions.summary.PermissionSummary as AvroPermissionSummary
import net.corda.data.permissions.summary.UserPermissionSummary as AvroUserPermissionSummary
import net.corda.data.permissions.PermissionType as AvroPermissionType
import net.corda.libs.permissions.storage.reader.summary.InternalUserPermissionSummary
import net.corda.permissions.model.PermissionType
import net.corda.permissions.query.dto.InternalPermissionQueryDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class PermissionSummaryReconcilerImplTest {

    private val reconciler = PermissionSummaryReconcilerImpl()

    @Test
    fun `getSummariesForReconciliation detects difference with removal of a permission summary`() {
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val earlier = now.minusMillis(1000)

        val dbSummary = InternalUserPermissionSummary(
            "loginName",
            listOf(
                InternalPermissionQueryDto("loginName", null, null, "A", PermissionType.ALLOW),
                InternalPermissionQueryDto("loginName", null, null, "B", PermissionType.DENY),
                InternalPermissionQueryDto("loginName", null, null, "C", PermissionType.DENY),
            ),
            now
        )
        val dbPermissionSummaries = mutableMapOf("loginName" to dbSummary)

        val cacheSummary = AvroUserPermissionSummary(
            "loginName",
            listOf(
                AvroPermissionSummary(null, null, "A", AvroPermissionType.ALLOW),
                AvroPermissionSummary(null, null, "B", AvroPermissionType.DENY),
                AvroPermissionSummary(null, null, "C", AvroPermissionType.DENY),
                AvroPermissionSummary(null, null, "D", AvroPermissionType.DENY),
            ),
            earlier
        )
        val cachedPermissionSummaries = mutableMapOf("loginName" to cacheSummary)

        val result = reconciler.getSummariesForReconciliation(
            dbPermissionSummaries,
            cachedPermissionSummaries
        )

        assertEquals(1, result.size)
        assertNotNull(result["loginName"])

        val resultAvroObject = result["loginName"]!!
        assertEquals(now, resultAvroObject.lastUpdateTimestamp)
        assertEquals("loginName", resultAvroObject.loginName)

        val resultPermissionsList = resultAvroObject.permissions
        assertEquals(3, resultPermissionsList.size)
        assertEquals("A", resultPermissionsList[0].permissionString)
        assertEquals("B", resultPermissionsList[1].permissionString)
        assertEquals("C", resultPermissionsList[2].permissionString)

        assertEquals(AvroPermissionType.ALLOW, resultPermissionsList[0].permissionType)
        assertEquals(AvroPermissionType.DENY, resultPermissionsList[1].permissionType)
        assertEquals(AvroPermissionType.DENY, resultPermissionsList[2].permissionType)
    }

    @Test
    fun `getSummariesForReconciliation detects difference with adding of a permission summary`() {

        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val earlier = now.minusMillis(1000)

        val dbSummary = InternalUserPermissionSummary(
            "loginName",
            listOf(
                InternalPermissionQueryDto("loginName", null, null, "A", PermissionType.ALLOW),
                InternalPermissionQueryDto("loginName", null, null, "B", PermissionType.DENY),
                InternalPermissionQueryDto("loginName", null, null, "C", PermissionType.DENY),
                InternalPermissionQueryDto("loginName", null, null, "D", PermissionType.DENY),
                InternalPermissionQueryDto("loginName", null, null, "E", PermissionType.DENY)
            ),
            now
        )
        val dbPermissionSummaries = mutableMapOf("loginName" to dbSummary)

        val cacheSummary = AvroUserPermissionSummary(
            "loginName",
            listOf(
                AvroPermissionSummary(null, null, "A", AvroPermissionType.ALLOW),
                AvroPermissionSummary(null, null, "B", AvroPermissionType.DENY),
                AvroPermissionSummary(null, null, "C", AvroPermissionType.DENY),
                AvroPermissionSummary(null, null, "D", AvroPermissionType.DENY),
            ),
            earlier
        )
        val cachedPermissionSummaries = mutableMapOf("loginName" to cacheSummary)

        val result = reconciler.getSummariesForReconciliation(
            dbPermissionSummaries,
            cachedPermissionSummaries
        )

        assertEquals(1, result.size)
        assertNotNull(result["loginName"])

        val resultAvroObject = result["loginName"]!!
        assertEquals(now, resultAvroObject.lastUpdateTimestamp)
        assertEquals("loginName", resultAvroObject.loginName)

        val resultPermissionsList = resultAvroObject.permissions
        assertEquals(5, resultPermissionsList.size)
        assertEquals("A", resultPermissionsList[0].permissionString)
        assertEquals("B", resultPermissionsList[1].permissionString)
        assertEquals("C", resultPermissionsList[2].permissionString)
        assertEquals("D", resultPermissionsList[3].permissionString)
        assertEquals("E", resultPermissionsList[4].permissionString)

        assertEquals(AvroPermissionType.ALLOW, resultPermissionsList[0].permissionType)
        assertEquals(AvroPermissionType.DENY, resultPermissionsList[1].permissionType)
        assertEquals(AvroPermissionType.DENY, resultPermissionsList[2].permissionType)
        assertEquals(AvroPermissionType.DENY, resultPermissionsList[3].permissionType)
        assertEquals(AvroPermissionType.DENY, resultPermissionsList[4].permissionType)
    }

    @Test
    fun `getSummariesForReconciliation detects difference with a permission string`() {

        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val earlier = now.minusMillis(1000)

        val dbSummary = InternalUserPermissionSummary(
            "loginName",
            listOf(
                InternalPermissionQueryDto("loginName", null, null, "A", PermissionType.ALLOW),
                InternalPermissionQueryDto("loginName", null, null, "F", PermissionType.DENY),
                InternalPermissionQueryDto("loginName", null, null, "C", PermissionType.DENY),
            ),
            now
        )
        val dbPermissionSummaries = mutableMapOf("loginName" to dbSummary)

        val cacheSummary = AvroUserPermissionSummary(
            "loginName",
            listOf(
                AvroPermissionSummary(null, null, "A", AvroPermissionType.ALLOW),
                AvroPermissionSummary(null, null, "B", AvroPermissionType.DENY),
                AvroPermissionSummary(null, null, "C", AvroPermissionType.DENY),
            ),
            earlier
        )
        val cachedPermissionSummaries = mutableMapOf("loginName" to cacheSummary)

        val result = reconciler.getSummariesForReconciliation(
            dbPermissionSummaries,
            cachedPermissionSummaries
        )

        assertEquals(1, result.size)
        assertNotNull(result["loginName"])

        val resultAvroObject = result["loginName"]!!
        assertEquals(now, resultAvroObject.lastUpdateTimestamp)
        assertEquals("loginName", resultAvroObject.loginName)

        val resultPermissionsList = resultAvroObject.permissions
        assertEquals(3, resultPermissionsList.size)
        assertEquals("A", resultPermissionsList[0].permissionString)
        assertEquals(AvroPermissionType.ALLOW, resultPermissionsList[0].permissionType)
        assertEquals("F", resultPermissionsList[1].permissionString)
        assertEquals(AvroPermissionType.DENY, resultPermissionsList[1].permissionType)
        assertEquals("C", resultPermissionsList[2].permissionString)
        assertEquals(AvroPermissionType.DENY, resultPermissionsList[2].permissionType)
    }

    @Test
    fun `getSummariesForReconciliation detects difference with a permission type`() {

        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val earlier = now.minusMillis(1000)

        val dbSummary = InternalUserPermissionSummary(
            "loginName",
            listOf(
                InternalPermissionQueryDto("loginName", null, null, "A", PermissionType.ALLOW),
                InternalPermissionQueryDto("loginName", null, null, "B", PermissionType.ALLOW),
                InternalPermissionQueryDto("loginName", null, null, "C", PermissionType.DENY),
            ),
            now
        )
        val dbPermissionSummaries = mutableMapOf("loginName" to dbSummary)

        val cacheSummary = AvroUserPermissionSummary(
            "loginName",
            listOf(
                AvroPermissionSummary(null, null, "A", AvroPermissionType.ALLOW),
                AvroPermissionSummary(null, null, "B", AvroPermissionType.DENY),
                AvroPermissionSummary(null, null, "C", AvroPermissionType.DENY),
            ),
            earlier
        )
        val cachedPermissionSummaries = mutableMapOf("loginName" to cacheSummary)

        val result = reconciler.getSummariesForReconciliation(
            dbPermissionSummaries,
            cachedPermissionSummaries
        )

        assertEquals(1, result.size)
        assertNotNull(result["loginName"])

        val resultAvroObject = result["loginName"]!!
        assertEquals(now, resultAvroObject.lastUpdateTimestamp)
        assertEquals("loginName", resultAvroObject.loginName)

        val resultPermissionsList = resultAvroObject.permissions
        assertEquals(3, resultPermissionsList.size)
        assertEquals("A", resultPermissionsList[0].permissionString)
        assertEquals("B", resultPermissionsList[1].permissionString)
        assertEquals("C", resultPermissionsList[2].permissionString)

        assertEquals(AvroPermissionType.ALLOW, resultPermissionsList[0].permissionType)
        assertEquals(AvroPermissionType.ALLOW, resultPermissionsList[1].permissionType)
        assertEquals(AvroPermissionType.DENY, resultPermissionsList[2].permissionType)
    }

    @Test
    fun `getSummariesForReconciliation detects difference with a groupVisibility`() {

        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val earlier = now.minusMillis(1000)

        val dbSummary = InternalUserPermissionSummary(
            "loginName",
            listOf(
                InternalPermissionQueryDto("loginName", "grp", null, "A", PermissionType.ALLOW)
            ),
            now
        )
        val dbPermissionSummaries = mutableMapOf("loginName" to dbSummary)

        val cacheSummary = AvroUserPermissionSummary(
            "loginName",
            listOf(
                AvroPermissionSummary(null, null, "A", AvroPermissionType.ALLOW)
            ),
            earlier
        )
        val cachedPermissionSummaries = mutableMapOf("loginName" to cacheSummary)

        val result = reconciler.getSummariesForReconciliation(
            dbPermissionSummaries,
            cachedPermissionSummaries
        )

        assertEquals(1, result.size)
        assertNotNull(result["loginName"])

        val resultAvroObject = result["loginName"]!!
        assertEquals(now, resultAvroObject.lastUpdateTimestamp)
        assertEquals("loginName", resultAvroObject.loginName)

        val resultPermissionsList = resultAvroObject.permissions
        assertEquals(1, resultPermissionsList.size)
        assertEquals("A", resultPermissionsList[0].permissionString)
        assertEquals(AvroPermissionType.ALLOW, resultPermissionsList[0].permissionType)
        assertEquals("grp", resultPermissionsList[0].groupVisibility)
        assertNull(resultPermissionsList[0].virtualNode)
    }

    @Test
    fun `getSummariesForReconciliation detects difference with a virtualNode`() {

        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val earlier = now.minusMillis(1000)

        val dbSummary = InternalUserPermissionSummary(
            "loginName",
            listOf(
                InternalPermissionQueryDto("loginName", null, "vrtnode", "A", PermissionType.ALLOW)
            ),
            now
        )
        val dbPermissionSummaries = mutableMapOf("loginName" to dbSummary)

        val cacheSummary = AvroUserPermissionSummary(
            "loginName",
            listOf(
                AvroPermissionSummary(null, null, "A", AvroPermissionType.ALLOW)
            ),
            earlier
        )
        val cachedPermissionSummaries = mutableMapOf("loginName" to cacheSummary)

        val result = reconciler.getSummariesForReconciliation(
            dbPermissionSummaries,
            cachedPermissionSummaries
        )

        assertEquals(1, result.size)
        assertNotNull(result["loginName"])

        val resultAvroObject = result["loginName"]!!
        assertEquals(now, resultAvroObject.lastUpdateTimestamp)
        assertEquals("loginName", resultAvroObject.loginName)

        val resultPermissionsList = resultAvroObject.permissions
        assertEquals(1, resultPermissionsList.size)
        assertEquals("A", resultPermissionsList[0].permissionString)
        assertEquals(AvroPermissionType.ALLOW, resultPermissionsList[0].permissionType)
        assertEquals("vrtnode", resultPermissionsList[0].virtualNode)
        assertNull(resultPermissionsList[0].groupVisibility)
    }

    @Test
    fun `getSummariesForReconciliation detects multiple differences`() {

        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val earlier = now.minusMillis(1000)

        val dbSummary = InternalUserPermissionSummary(
            "loginName",
            listOf(
                InternalPermissionQueryDto("loginName", null, "vrtnode", "A", PermissionType.ALLOW),
                InternalPermissionQueryDto("loginName", "grp", null, "B", PermissionType.DENY)
            ),
            now
        )
        val dbPermissionSummaries = mutableMapOf("loginName" to dbSummary)

        val cacheSummary = AvroUserPermissionSummary(
            "loginName",
            listOf(
                AvroPermissionSummary("cgrp", null, "C", AvroPermissionType.DENY)
            ),
            earlier
        )
        val cachedPermissionSummaries = mutableMapOf("loginName" to cacheSummary)

        val result = reconciler.getSummariesForReconciliation(
            dbPermissionSummaries,
            cachedPermissionSummaries
        )

        assertEquals(1, result.size)
        assertNotNull(result["loginName"])

        val resultAvroObject = result["loginName"]!!
        assertEquals(now, resultAvroObject.lastUpdateTimestamp)
        assertEquals("loginName", resultAvroObject.loginName)

        val resultPermissionsList = resultAvroObject.permissions
        assertEquals(2, resultPermissionsList.size)
        assertEquals("A", resultPermissionsList[0].permissionString)
        assertEquals("B", resultPermissionsList[1].permissionString)
        assertEquals(AvroPermissionType.ALLOW, resultPermissionsList[0].permissionType)
        assertEquals(AvroPermissionType.DENY, resultPermissionsList[1].permissionType)
        assertEquals("vrtnode", resultPermissionsList[0].virtualNode)
        assertNull(resultPermissionsList[1].virtualNode)
        assertNull(resultPermissionsList[0].groupVisibility)
        assertEquals("grp", resultPermissionsList[1].groupVisibility)
    }

    @Test
    fun `getSummariesForReconciliation detects no difference when permissions are the same`() {

        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val earlier = now.minusMillis(1000)

        val dbSummary = InternalUserPermissionSummary(
            "loginName",
            listOf(
                InternalPermissionQueryDto("loginName", null, null, "A", PermissionType.ALLOW),
                InternalPermissionQueryDto("loginName", null, null, "B", PermissionType.DENY),
                InternalPermissionQueryDto("loginName", null, null, "C", PermissionType.DENY),
            ),
            now
        )
        val dbPermissionSummaries = mutableMapOf("loginName" to dbSummary)

        val cacheSummary = AvroUserPermissionSummary(
            "loginName",
            listOf(
                AvroPermissionSummary(null, null, "A", AvroPermissionType.ALLOW),
                AvroPermissionSummary(null, null, "B", AvroPermissionType.DENY),
                AvroPermissionSummary(null, null, "C", AvroPermissionType.DENY),
            ),
            earlier
        )
        val cachedPermissionSummaries = mutableMapOf("loginName" to cacheSummary)

        val result = reconciler.getSummariesForReconciliation(
            dbPermissionSummaries,
            cachedPermissionSummaries
        )

        assertEquals(0, result.size)
        assertNull(result["loginName"])
    }

    @Test
    fun `getSummariesForReconciliation detects when a user was removed`() {

        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val earlier = now.minusMillis(1000)

        val dbSummary = InternalUserPermissionSummary(
            "loginName",
            listOf(
                InternalPermissionQueryDto("loginName", null, null, "A", PermissionType.ALLOW),
                InternalPermissionQueryDto("loginName", null, null, "B", PermissionType.DENY)
            ),
            now
        )
        val dbPermissionSummaries = mutableMapOf("loginName" to dbSummary)

        val cacheSummary1 = AvroUserPermissionSummary(
            "loginName",
            listOf(
                AvroPermissionSummary(null, null, "A", AvroPermissionType.ALLOW),
                AvroPermissionSummary(null, null, "B", AvroPermissionType.DENY)
            ),
            earlier
        )
        val cacheSummary2 = AvroUserPermissionSummary(
            "removedUser",
            listOf(
                AvroPermissionSummary(null, null, "A", AvroPermissionType.ALLOW),
                AvroPermissionSummary(null, null, "B", AvroPermissionType.DENY)
            ),
            earlier
        )
        val cachedPermissionSummaries = mutableMapOf(
            "loginName" to cacheSummary1,
            "removedUser" to cacheSummary2
        )

        val result = reconciler.getSummariesForReconciliation(
            dbPermissionSummaries,
            cachedPermissionSummaries
        )

        assertEquals(1, result.size)
        assertNull(result["removedUser"])
    }

    @Test
    fun `getSummariesForReconciliation detects when a user was added`() {

        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val earlier = now.minusMillis(1000)

        val dbPermissionSummaries = mutableMapOf(
            "loginName" to InternalUserPermissionSummary(
                "loginName",
                listOf(
                    InternalPermissionQueryDto("loginName", null, null, "A", PermissionType.ALLOW)
                ),
                now
            ),
            "addedUser" to InternalUserPermissionSummary(
                "addedUser",
                listOf(
                    InternalPermissionQueryDto("addedUser", "grp", "vrtnode", "A", PermissionType.ALLOW)
                ),
                now
            )
        )

        val cachedPermissionSummaries = mutableMapOf(
            "loginName" to AvroUserPermissionSummary(
                "loginName",
                listOf(
                    AvroPermissionSummary(null, null, "A", AvroPermissionType.ALLOW)
                ),
                earlier
            )
        )

        val result = reconciler.getSummariesForReconciliation(
            dbPermissionSummaries,
            cachedPermissionSummaries
        )

        assertEquals(1, result.size)
        assertNotNull(result["addedUser"])

        val resultAvroObject = result["addedUser"]!!
        assertEquals(now, resultAvroObject.lastUpdateTimestamp)
        assertEquals("addedUser", resultAvroObject.loginName)

        val resultPermissionsList = resultAvroObject.permissions
        assertEquals(1, resultPermissionsList.size)
        assertEquals("A", resultPermissionsList[0].permissionString)
        assertEquals(AvroPermissionType.ALLOW, resultPermissionsList[0].permissionType)
        assertEquals("vrtnode", resultPermissionsList[0].virtualNode)
        assertEquals("grp", resultPermissionsList[0].groupVisibility)
    }

    @Test
    fun `getSummariesForReconciliation detects when a user summary calculation from db is actually older than summary the cache`() {

        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val later = now.plusMillis(1000)

        val dbPermissionSummaries = mutableMapOf(
            "loginName" to InternalUserPermissionSummary(
                "loginName",
                listOf(
                    InternalPermissionQueryDto("loginName", null, null, "A", PermissionType.ALLOW)
                ),
                now
            )
        )

        val cachedPermissionSummaries = mutableMapOf(
            "loginName" to AvroUserPermissionSummary(
                "loginName",
                listOf(
                    AvroPermissionSummary(null, null, "B", AvroPermissionType.ALLOW)
                ),
                later
            )
        )

        val result = reconciler.getSummariesForReconciliation(
            dbPermissionSummaries,
            cachedPermissionSummaries
        )

        assertEquals(0, result.size)
        assertNull(result["loginName"])
    }
}