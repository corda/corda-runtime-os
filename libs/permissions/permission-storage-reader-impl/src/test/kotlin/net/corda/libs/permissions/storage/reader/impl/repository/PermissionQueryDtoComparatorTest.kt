package net.corda.libs.permissions.storage.reader.impl.repository

import net.corda.permissions.model.PermissionType
import net.corda.permissions.query.dto.InternalPermissionQueryDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PermissionQueryDtoComparatorTest {


    @Test
    fun `DENY should appear in sorted list before ALLOW`() {
        val perm1 = InternalPermissionQueryDto("user1", "id", null, null, "ABC", PermissionType.ALLOW)
        val perm2 = InternalPermissionQueryDto("user1", "id", null, null, "XYZ", PermissionType.DENY)
        val list = listOf(perm1, perm2)

        val sortedList = list.sortedWith(PermissionQueryDtoComparator())

        assertEquals(perm2, sortedList[0])
        assertEquals(perm1, sortedList[1])
    }

    @Test
    fun `permission string sorted alphabetically`() {
        val perm1 = InternalPermissionQueryDto("user1", "id", null, null, "ABC", PermissionType.DENY)
        val perm2 = InternalPermissionQueryDto("user1", "id", null, null, "XYZ", PermissionType.DENY)
        val list = listOf(perm2, perm1)

        val sortedList = list.sortedWith(PermissionQueryDtoComparator())

        assertEquals(perm1, sortedList[0])
        assertEquals(perm2, sortedList[1])
    }

    @Test
    fun `virtual node null first`() {
        val perm1 = InternalPermissionQueryDto("user1", "id", null, "abc", "ABC", PermissionType.DENY)
        val perm2 = InternalPermissionQueryDto("user1", "id", null, null, "ABC", PermissionType.DENY)
        val list = listOf(perm1, perm2)

        val sortedList = list.sortedWith(PermissionQueryDtoComparator())

        assertEquals(perm2, sortedList[0])
        assertEquals(perm1, sortedList[1])
    }

    @Test
    fun `virtual node alphabetically ordered`() {
        val perm1 = InternalPermissionQueryDto("user1", "id", null, "zzz", "ABC", PermissionType.DENY)
        val perm2 = InternalPermissionQueryDto("user1", "id", null, "aaa", "ABC", PermissionType.DENY)
        val list = listOf(perm1, perm2)

        val sortedList = list.sortedWith(PermissionQueryDtoComparator())

        assertEquals(perm2, sortedList[0])
        assertEquals(perm1, sortedList[1])
    }

    @Test
    fun `group visibility null first`() {
        val perm1 = InternalPermissionQueryDto("user1", "id", "aaa", "aaa", "ABC", PermissionType.DENY)
        val perm2 = InternalPermissionQueryDto("user1", "id", null, "aaa", "ABC", PermissionType.DENY)
        val list = listOf(perm1, perm2)

        val sortedList = list.sortedWith(PermissionQueryDtoComparator())

        assertEquals(perm2, sortedList[0])
        assertEquals(perm1, sortedList[1])
    }

    @Test
    fun `group visibility alphabetically sorted`() {
        val perm1 = InternalPermissionQueryDto("user1", "id", "zzz", "aaa", "ABC", PermissionType.DENY)
        val perm2 = InternalPermissionQueryDto("user1", "id", "aaa", "aaa", "ABC", PermissionType.DENY)
        val list = listOf(perm1, perm2)

        val sortedList = list.sortedWith(PermissionQueryDtoComparator())

        assertEquals(perm2, sortedList[0])
        assertEquals(perm1, sortedList[1])
    }

    @Test
    fun `lastly permission id should be sorted alphabetically`() {
        val perm1 = InternalPermissionQueryDto("user1", "bbb", "zzz", "aaa", "ABC", PermissionType.DENY)
        val perm2 = InternalPermissionQueryDto("user1", "aaa", "aaa", "aaa", "ABC", PermissionType.DENY)
        val list = listOf(perm1, perm2)

        val sortedList = list.sortedWith(PermissionQueryDtoComparator())

        assertEquals(perm2, sortedList[0])
        assertEquals(perm1, sortedList[1])
    }
}