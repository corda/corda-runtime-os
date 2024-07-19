package net.corda.libs.permissions.storage.reader.impl.repository

import net.corda.libs.permissions.storage.reader.repository.PermissionRepository
import net.corda.libs.permissions.storage.reader.repository.UserLogin
import net.corda.libs.permissions.storage.reader.summary.InternalUserPermissionSummary
import net.corda.libs.permissions.storage.reader.util.PermissionUserUtil
import net.corda.orm.utils.transaction
import net.corda.permissions.model.Group
import net.corda.permissions.model.Permission
import net.corda.permissions.model.Role
import net.corda.permissions.model.User
import net.corda.permissions.query.dto.InternalPermissionQueryDto
import net.corda.permissions.query.dto.InternalUserEnabledQueryDto
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

@Suppress("TooManyFunctions")
class PermissionRepositoryImpl(private val entityManagerFactory: EntityManagerFactory) : PermissionRepository {

    override fun findAllUsers(): List<User> {
        return findAll("SELECT u from User u")
    }

    override fun findAllGroups(): List<Group> {
        return findAll("SELECT g from Group g")
    }

    override fun findAllRoles(): List<Role> {
        return findAll("SELECT r from Role r")
    }

    override fun findAllPermissions(): List<Permission> {
        return findAll("SELECT p from Permission p")
    }

    override fun findAllUsers(ids: List<String>): List<User> {
        return findAll("SELECT u from User u WHERE u.id IN :ids", ids)
    }

    override fun findAllGroups(ids: List<String>): List<Group> {
        return findAll("SELECT g from Group g WHERE g.id IN :ids", ids)
    }

    override fun findAllRoles(ids: List<String>): List<Role> {
        return findAll("SELECT r from Role r WHERE r.id IN :ids", ids)
    }

    override fun findAllPermissionSummaries(): Map<UserLogin, InternalUserPermissionSummary> {
        entityManagerFactory.transaction { entityManager ->
            val timeOfPermissionSummary = Instant.now()
            val userLoginsWithEnabledFlag = findAllUserLoginsAndEnabledFlags(entityManager)
            val userPermissionsFromRolesAndGroups = PermissionUserUtil.calculatePermissionsForUsers(entityManager)

            return aggregatePermissionSummariesForUsers(
                userLoginsWithEnabledFlag,
                userPermissionsFromRolesAndGroups,
                timeOfPermissionSummary
            )
        }
    }

    private inline fun <reified T> findAll(qlString: String): List<T> {
        return entityManagerFactory.transaction { entityManager ->
            entityManager.createQuery(qlString, T::class.java).resultList
        }
    }

    private inline fun <reified T> findAll(qlString: String, ids: List<String>): List<T> {
        return entityManagerFactory.transaction { entityManager ->
            ids.chunked(100) { chunkedIds ->
                entityManager.createQuery(qlString, T::class.java)
                    .setParameter("ids", chunkedIds)
                    .resultList
            }.flatten()
        }
    }

    private fun aggregatePermissionSummariesForUsers(
        userLogins: List<InternalUserEnabledQueryDto>,
        userPermissionsFromRolesAndGroups: Map<UserLogin, List<InternalPermissionQueryDto>>,
        timeOfPermissionSummary: Instant,
    ): Map<String, InternalUserPermissionSummary> {
        return userLogins.associateBy({ user -> user.loginName }) { user ->
            // rolePermissionsQuery features inner joins so a user without roles won't be present in this map
            val permissionsInheritedFromRoles = (
                (userPermissionsFromRolesAndGroups[user.loginName] ?: emptyList()).toSortedSet(PermissionQueryDtoComparator())
                )

            InternalUserPermissionSummary(
                user.loginName,
                user.enabled,
                permissionsInheritedFromRoles,
                timeOfPermissionSummary
            )
        }
    }

    private fun findAllUserLoginsAndEnabledFlags(em: EntityManager): List<InternalUserEnabledQueryDto> {
        val query = "SELECT NEW net.corda.permissions.query.dto.InternalUserEnabledQueryDto(u.loginName, u.enabled) FROM User u"
        return em.createQuery(query, InternalUserEnabledQueryDto::class.java)
            .resultList
    }
}
