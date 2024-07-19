package net.corda.libs.permissions.storage.reader.impl.util

import net.corda.libs.permissions.storage.reader.util.PermissionUserUtil.aggregatePermissionSummariesForUsers
import net.corda.libs.permissions.storage.reader.util.PermissionUserUtil.allGroupPermissionsQuery
import net.corda.libs.permissions.storage.reader.util.PermissionUserUtil.allUsersPermissionsQuery
import net.corda.libs.permissions.storage.reader.util.PermissionUserUtil.calculatePermissionsForUsers
import net.corda.permissions.model.PermissionType
import net.corda.permissions.query.dto.InternalPermissionQueryDto
import net.corda.permissions.query.dto.InternalPermissionWithParentGroupQueryDto
import net.corda.permissions.query.dto.InternalUserEnabledQueryDto
import net.corda.permissions.query.dto.Permission
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.time.Instant
import javax.persistence.EntityManager

class PermissionUserUtilTest {

    private fun createEntityManager(
        groupPermissionEntity: List<InternalPermissionWithParentGroupQueryDto>,
        userPermissionEntity: List<InternalPermissionWithParentGroupQueryDto>
    ): EntityManager = mock<EntityManager> {
        on {
            createQuery(
                eq(allGroupPermissionsQuery),
                eq(InternalPermissionWithParentGroupQueryDto::class.java)
            )
        } doAnswer {
            mock {
                on { resultList } doReturn groupPermissionEntity
            }
        }

        on {
            createQuery(
                eq(allUsersPermissionsQuery),
                eq(InternalPermissionWithParentGroupQueryDto::class.java)
            )
        } doAnswer {
            mock {
                on { resultList } doReturn userPermissionEntity
            }
        }
    }

    @Test
    fun `empty list of user and group permissions returns an empty map`() {
        val calculatedPermissions = calculatePermissionsForUsers(
            createEntityManager(
                emptyList(),
                emptyList()
            )
        )
        assertThat(calculatedPermissions).isEmpty()
    }

    @Test
    fun `list of group permissions and empty list of user permissions returns an empty map`() {
        val calculatedPermissions = calculatePermissionsForUsers(
            createEntityManager(
                listOf(
                    InternalPermissionWithParentGroupQueryDto(
                        "groupId1",
                        "permissionId1",
                        null,
                        null,
                        "ABC",
                        PermissionType.ALLOW,
                        null,
                        null
                    ),
                    InternalPermissionWithParentGroupQueryDto(
                        "groupId2",
                        "permissionId2",
                        null,
                        null,
                        "XYZ",
                        PermissionType.ALLOW,
                        "groupId1",
                        null
                    ),
                    InternalPermissionWithParentGroupQueryDto(
                        "groupId3",
                        "permissionId3",
                        null,
                        null,
                        "XYZ",
                        PermissionType.DENY,
                        "groupId1",
                        null
                    )
                ),
                emptyList()
            )
        )
        assertThat(calculatedPermissions).isEmpty()
    }

    @Test
    fun `list of user permissions and empty list of group permissions returns the same user permissions`() {
        val calculatedPermissions = calculatePermissionsForUsers(
            createEntityManager(
                emptyList(),
                listOf(
                    InternalPermissionWithParentGroupQueryDto(
                        "userId1",
                        "permissionId1",
                        null,
                        null,
                        "ABC",
                        PermissionType.ALLOW,
                        null,
                        "user1"
                    ),
                    InternalPermissionWithParentGroupQueryDto(
                        "userId1",
                        "permissionId2",
                        null,
                        null,
                        "XYZ",
                        PermissionType.ALLOW,
                        null,
                        "user1"
                    ),
                    InternalPermissionWithParentGroupQueryDto(
                        "userId1",
                        "permissionId3",
                        null,
                        null,
                        "XYZ",
                        PermissionType.DENY,
                        null,
                        "user1"
                    )
                )
            )
        )
        assertThat(calculatedPermissions).containsOnlyKeys("user1")
        assertThat(calculatedPermissions["user1"]?.permissionsList).size().isEqualTo(3)
        assertThat(calculatedPermissions["user1"]?.permissionsList).containsExactlyInAnyOrder(
            Permission("permissionId1", null, null, "ABC", PermissionType.ALLOW),
            Permission("permissionId2", null, null, "XYZ", PermissionType.ALLOW),
            Permission("permissionId3", null, null, "XYZ", PermissionType.DENY)
        )
    }

    @Test
    fun `single user with parent group hierarchy returns the permissions of the user and its associated groups`() {
        val calculatedPermissions = calculatePermissionsForUsers(
            createEntityManager(
                listOf(
                    InternalPermissionWithParentGroupQueryDto(
                        "groupId1",
                        "permissionId1",
                        null,
                        null,
                        "ABC",
                        PermissionType.ALLOW,
                        null,
                        null
                    ),
                    InternalPermissionWithParentGroupQueryDto(
                        "groupId2",
                        "permissionId2",
                        null,
                        null,
                        "XYZ",
                        PermissionType.ALLOW,
                        "groupId1",
                        null
                    ),
                    InternalPermissionWithParentGroupQueryDto(
                        "groupId3",
                        "permissionId3",
                        null,
                        null,
                        "DEF",
                        PermissionType.ALLOW,
                        "groupId2",
                        null
                    )
                ),
                listOf(
                    InternalPermissionWithParentGroupQueryDto(
                        "userId1",
                        "permissionId4",
                        null,
                        null,
                        "GHI",
                        PermissionType.ALLOW,
                        "groupId3",
                        "user1"
                    ),
                    InternalPermissionWithParentGroupQueryDto(
                        "userId1",
                        "permissionId5",
                        null,
                        null,
                        "XYZ",
                        PermissionType.ALLOW,
                        "groupId3",
                        "user1"
                    ),
                    InternalPermissionWithParentGroupQueryDto(
                        "userId1",
                        "permissionId6",
                        null,
                        null,
                        "XYZ",
                        PermissionType.DENY,
                        "groupId3",
                        "user1"
                    )
                )
            )
        )
        assertThat(calculatedPermissions).containsOnlyKeys("user1")
        assertThat(calculatedPermissions["user1"]?.permissionsList).size().isEqualTo(6)
        assertThat(calculatedPermissions["user1"]?.permissionsList).containsExactlyInAnyOrder(
            Permission("permissionId1", null, null, "ABC", PermissionType.ALLOW),
            Permission("permissionId2", null, null, "XYZ", PermissionType.ALLOW),
            Permission("permissionId3", null, null, "DEF", PermissionType.ALLOW),
            Permission("permissionId4", null, null, "GHI", PermissionType.ALLOW),
            Permission("permissionId5", null, null, "XYZ", PermissionType.ALLOW),
            Permission("permissionId6", null, null, "XYZ", PermissionType.DENY)
        )
    }

    @Test
    fun `users at different group levels return the correct number of permissions`() {
        val calculatedPermissions = calculatePermissionsForUsers(
            createEntityManager(
                listOf(
                    InternalPermissionWithParentGroupQueryDto(
                        "groupId1",
                        "permissionId1",
                        null,
                        null,
                        "ABC",
                        PermissionType.ALLOW,
                        null,
                        null
                    ),
                    InternalPermissionWithParentGroupQueryDto(
                        "groupId2",
                        "permissionId2",
                        null,
                        null,
                        "XYZ",
                        PermissionType.ALLOW,
                        "groupId1",
                        null
                    ),
                    InternalPermissionWithParentGroupQueryDto(
                        "groupId3",
                        "permissionId3",
                        null,
                        null,
                        "DEF",
                        PermissionType.ALLOW,
                        "groupId2",
                        null
                    ),
                    InternalPermissionWithParentGroupQueryDto(
                        "groupId4",
                        "permissionId4",
                        null,
                        null,
                        "GHI",
                        PermissionType.ALLOW,
                        "groupId1",
                        null
                    ),
                    InternalPermissionWithParentGroupQueryDto(
                        "groupId5",
                        "permissionId5",
                        null,
                        null,
                        "JKL",
                        PermissionType.ALLOW,
                        "groupId2",
                        null
                    ),
                    InternalPermissionWithParentGroupQueryDto(
                        "groupId6",
                        "permissionId6",
                        null,
                        null,
                        "MNO",
                        PermissionType.ALLOW,
                        "groupId3",
                        null
                    ),
                ),
                listOf(
                    InternalPermissionWithParentGroupQueryDto(
                        "userId1",
                        "permissionId7",
                        null,
                        null,
                        "GHI",
                        PermissionType.DENY,
                        "groupId6",
                        "user1"
                    ),
                    InternalPermissionWithParentGroupQueryDto("userId2", null, null, null, null, null, "groupId3", "user2"),
                    InternalPermissionWithParentGroupQueryDto("userId3", null, null, null, null, null, "groupId5", "user3"),
                    InternalPermissionWithParentGroupQueryDto("userId4", null, null, null, null, null, "groupId2", "user4"),
                    InternalPermissionWithParentGroupQueryDto("userId5", null, null, null, null, null, "groupId4", "user5"),
                    InternalPermissionWithParentGroupQueryDto("userId6", null, null, null, null, null, "groupId1", "user6")
                )
            )
        )
        assertThat(calculatedPermissions).containsOnlyKeys("user1", "user2", "user3", "user4", "user5", "user6")
        assertThat(calculatedPermissions["user1"]?.permissionsList).size().isEqualTo(5)
        assertThat(calculatedPermissions["user2"]?.permissionsList).size().isEqualTo(3)
        assertThat(calculatedPermissions["user3"]?.permissionsList).size().isEqualTo(3)
        assertThat(calculatedPermissions["user4"]?.permissionsList).size().isEqualTo(2)
        assertThat(calculatedPermissions["user5"]?.permissionsList).size().isEqualTo(2)
        assertThat(calculatedPermissions["user6"]?.permissionsList).size().isEqualTo(1)
    }

    @Test
    fun `users and groups without any permissions are filtered out and aggregation adds user with empty list of permissions`() {
        val calculatedPermissions = calculatePermissionsForUsers(
            createEntityManager(
                listOf(
                    InternalPermissionWithParentGroupQueryDto(
                        "groupId1",
                        "permissionId1",
                        null,
                        null,
                        "ABC",
                        PermissionType.ALLOW,
                        "groupId2",
                        null
                    ),
                    InternalPermissionWithParentGroupQueryDto("groupId2", null, null, null, null, null, null, null),
                ),
                listOf(
                    InternalPermissionWithParentGroupQueryDto("userId1", null, null, null, null, null, "groupId2", "user1")
                )
            )
        )
        assertThat(calculatedPermissions["user1"]?.permissionsList).isEmpty()

        val aggregatedPermissions = aggregatePermissionSummariesForUsers(
            listOf(
                InternalUserEnabledQueryDto("user1", true)
            ),
            calculatedPermissions,
            Instant.now()
        )
        assertThat(aggregatedPermissions).containsOnlyKeys("user1")
        assertThat(aggregatedPermissions["user1"]!!.permissions).isEmpty()
    }

    @Test
    fun `user without permissions but has inherited permissions from groups is correctly returned`() {
        val calculatedPermissions = calculatePermissionsForUsers(
            createEntityManager(
                listOf(
                    InternalPermissionWithParentGroupQueryDto(
                        "groupId1",
                        "permissionId1",
                        null,
                        null,
                        "ABC",
                        PermissionType.ALLOW,
                        null,
                        null
                    ),
                    InternalPermissionWithParentGroupQueryDto(
                        "groupId2",
                        "permissionId2",
                        null,
                        null,
                        "XYZ",
                        PermissionType.ALLOW,
                        "groupId1",
                        null
                    ),
                    InternalPermissionWithParentGroupQueryDto(
                        "groupId3",
                        "permissionId3",
                        null,
                        null,
                        "DEF",
                        PermissionType.ALLOW,
                        "groupId2",
                        null
                    ),
                ),
                listOf(
                    InternalPermissionWithParentGroupQueryDto("userId1", null, null, null, null, null, "groupId3", "user1"),
                )
            )
        )
        assertThat(calculatedPermissions).containsOnlyKeys("user1")
        assertThat(calculatedPermissions["user1"]?.permissionsList).size().isEqualTo(3)
        assertThat(calculatedPermissions["user1"]?.permissionsList).containsExactlyInAnyOrder(
            Permission("permissionId1", null, null, "ABC", PermissionType.ALLOW),
            Permission("permissionId2", null, null, "XYZ", PermissionType.ALLOW),
            Permission("permissionId3", null, null, "DEF", PermissionType.ALLOW)
        )
    }

    @Test
    fun `user with parent group without permissions but has inherited permissions from grandparent group is correctly returned`() {
        val calculatedPermissions = calculatePermissionsForUsers(
            createEntityManager(
                listOf(
                    InternalPermissionWithParentGroupQueryDto("groupId1", null, null, null, null, null, null, null),
                    InternalPermissionWithParentGroupQueryDto(
                        "groupId2",
                        "permissionId1",
                        null,
                        null,
                        "XYZ",
                        PermissionType.ALLOW,
                        "groupId1",
                        null
                    ),
                    InternalPermissionWithParentGroupQueryDto("groupId3", null, null, null, null, null, "groupId2", null),
                ),
                listOf(
                    InternalPermissionWithParentGroupQueryDto("userId1", null, null, null, null, null, "groupId3", "user1"),
                )
            )
        )
        assertThat(calculatedPermissions).containsOnlyKeys("user1")
        assertThat(calculatedPermissions["user1"]?.permissionsList).size().isEqualTo(1)
        assertThat(calculatedPermissions["user1"]?.permissionsList).containsExactlyInAnyOrder(
            Permission("permissionId1", null, null, "XYZ", PermissionType.ALLOW)
        )
    }

    @Test
    fun `multiple trees of different groups and associated users test`() {
        val calculatedPermissions = calculatePermissionsForUsers(
            createEntityManager(
                listOf(
                    InternalPermissionWithParentGroupQueryDto(
                        "groupId1",
                        "permissionId1",
                        null,
                        null,
                        "ABC",
                        PermissionType.ALLOW,
                        null,
                        null
                    ),
                    InternalPermissionWithParentGroupQueryDto(
                        "groupId2",
                        "permissionId2",
                        null,
                        null,
                        "XYZ",
                        PermissionType.ALLOW,
                        "groupId1",
                        null
                    ),
                    InternalPermissionWithParentGroupQueryDto(
                        "groupId3",
                        "permissionId3",
                        null,
                        null,
                        "DEF",
                        PermissionType.ALLOW,
                        "groupId1",
                        null
                    ),
                    InternalPermissionWithParentGroupQueryDto(
                        "groupId4",
                        "permissionId4",
                        null,
                        null,
                        "GHI",
                        PermissionType.ALLOW,
                        null,
                        null
                    ),
                    InternalPermissionWithParentGroupQueryDto(
                        "groupId5",
                        "permissionId5",
                        null,
                        null,
                        "JKL",
                        PermissionType.ALLOW,
                        null,
                        null
                    ),
                    InternalPermissionWithParentGroupQueryDto(
                        "groupId6",
                        "permissionId6",
                        null,
                        null,
                        "MNO",
                        PermissionType.ALLOW,
                        "groupId5",
                        null
                    ),
                    InternalPermissionWithParentGroupQueryDto("groupId7", null, null, null, null, null, null, null)
                ),
                listOf(
                    InternalPermissionWithParentGroupQueryDto("userId1", null, null, null, null, null, "groupId2", "user1"),
                    InternalPermissionWithParentGroupQueryDto("userId2", null, null, null, null, null, "groupId3", "user2"),
                    InternalPermissionWithParentGroupQueryDto("userId3", null, null, null, null, null, "groupId4", "user3"),
                    InternalPermissionWithParentGroupQueryDto("userId4", null, null, null, null, null, "groupId6", "user4"),
                    InternalPermissionWithParentGroupQueryDto(
                        "userId5",
                        "permissionId7",
                        null,
                        null,
                        "ABC",
                        PermissionType.DENY,
                        "groupId6",
                        "user5"
                    ),
                    InternalPermissionWithParentGroupQueryDto("userId6", null, null, null, null, null, "groupId5", "user6"),
                    InternalPermissionWithParentGroupQueryDto("userId7", null, null, null, null, null, "groupId7", "user7"),
                    InternalPermissionWithParentGroupQueryDto("userId8", null, null, null, null, null, null, "user8")
                )
            )
        )
        assertThat(calculatedPermissions).containsOnlyKeys("user1", "user2", "user3", "user4", "user5", "user6", "user7", "user8")
        assertThat(calculatedPermissions["user1"]?.permissionsList).size().isEqualTo(2)
        assertThat(calculatedPermissions["user2"]?.permissionsList).size().isEqualTo(2)
        assertThat(calculatedPermissions["user3"]?.permissionsList).size().isEqualTo(1)
        assertThat(calculatedPermissions["user4"]?.permissionsList).size().isEqualTo(2)
        assertThat(calculatedPermissions["user5"]?.permissionsList).size().isEqualTo(3)
        assertThat(calculatedPermissions["user6"]?.permissionsList).size().isEqualTo(1)
        assertThat(calculatedPermissions["user7"]?.permissionsList).size().isEqualTo(0)
        assertThat(calculatedPermissions["user8"]?.permissionsList).size().isEqualTo(0)
    }

    @Test
    fun `aggregation function will remove duplicate permissions on the same user and sort DENY before ALLOW for the same permission`() {
        val calculatedPermissions = calculatePermissionsForUsers(
            createEntityManager(
                listOf(
                    InternalPermissionWithParentGroupQueryDto(
                        "groupId1",
                        "permissionId1",
                        null,
                        null,
                        "ABC",
                        PermissionType.ALLOW,
                        null,
                        null
                    ),
                    InternalPermissionWithParentGroupQueryDto(
                        "groupId1",
                        "permissionId2",
                        null,
                        null,
                        "ABC",
                        PermissionType.DENY,
                        null,
                        null
                    ),
                    InternalPermissionWithParentGroupQueryDto("groupId2", null, null, null, null, null, "groupId1", null),
                ),
                listOf(
                    InternalPermissionWithParentGroupQueryDto(
                        "userId1",
                        "permissionId2",
                        null,
                        null,
                        "ABC",
                        PermissionType.DENY,
                        "groupId2",
                        "user1"
                    ),
                    InternalPermissionWithParentGroupQueryDto(
                        "userId1",
                        "permissionId1",
                        null,
                        null,
                        "ABC",
                        PermissionType.ALLOW,
                        "groupId2",
                        "user1"
                    ),
                )
            )
        )
        assertThat(calculatedPermissions).containsOnlyKeys("user1")
        assertThat(calculatedPermissions["user1"]?.permissionsList?.size).isEqualTo(4)

        val aggregatedPermissions = aggregatePermissionSummariesForUsers(
            listOf(
                InternalUserEnabledQueryDto("user1", true)
            ),
            calculatedPermissions,
            Instant.now()
        )
        assertThat(aggregatedPermissions).containsOnlyKeys("user1")
        assertThat(aggregatedPermissions["user1"]!!.permissions.size).isEqualTo(2)

        // containsExactly() will check the order of the elements and we should expect DENY first
        assertThat(aggregatedPermissions["user1"]!!.permissions).containsExactly(
            InternalPermissionQueryDto("user1", "permissionId2", null, null, "ABC", PermissionType.DENY),
            InternalPermissionQueryDto("user1", "permissionId1", null, null, "ABC", PermissionType.ALLOW)
        )
    }
}
