package net.corda.libs.permissions.storage.writer.impl

import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.data.permissions.management.user.CreateUserRequest
import net.corda.permissions.model.Group
import net.corda.permissions.model.User
import net.corda.v5.base.concurrent.getOrThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.Query

class PermissionStorageWriterProcessorTest {

    // Not static so that it can be modified in the tests
    private val createUserRequest = CreateUserRequest().apply {
        fullName = "Dan Newton"
        loginName = "lankydan"
        enabled = true
    }

    private val entityTransaction = mock<EntityTransaction>()
    private val entityManager = mock<EntityManager>().apply {
        whenever(transaction).thenReturn(entityTransaction)
    }
    private val entityManagerFactory = mock<EntityManagerFactory>().apply {
        whenever(createEntityManager()).thenReturn(entityManager)
    }
    private val query = mock<Query>()

    private val processor = PermissionStorageWriterProcessor(entityManagerFactory)

    @Test
    fun `receiving invalid request completes exceptionally`() {
        val future = CompletableFuture<PermissionManagementResponse>()
        processor.onNext(
            request = PermissionManagementRequest().apply {
                request = Unit
            },
            respFuture = future
        )
        assertThrows<IllegalArgumentException> { future.getOrThrow() }
        verify(entityManager, never()).persist(any())
    }

    @Test
    fun `receiving CreateUserRequest persists a new user to the database`() {
        whenever(query.setParameter(any<String>(), any())).thenReturn(query)
        whenever(query.singleResult).thenReturn(0)
        whenever(entityManager.createQuery(any<String>())).thenReturn(query)
        val future = CompletableFuture<PermissionManagementResponse>()
        processor.onNext(
            request = PermissionManagementRequest().apply {
                request = createUserRequest
            },
            respFuture = future
        )
        assertTrue(future.getOrThrow().response is net.corda.data.permissions.User)
        (future.getOrThrow().response as? net.corda.data.permissions.User)?.let { response ->
            assertEquals(response.fullName, createUserRequest.fullName)
            assertEquals(response.enabled, createUserRequest.enabled)
        }
        verify(entityManager, times(1)).persist(any<User>())
    }

    @Test
    fun `receiving CreateUserRequest when a user with the same login name already exists completes exceptionally`() {
        whenever(query.setParameter(any<String>(), any())).thenReturn(query)
        whenever(query.singleResult).thenReturn(1)
        whenever(entityManager.createQuery(any<String>())).thenReturn(query)
        val future = CompletableFuture<PermissionManagementResponse>()
        processor.onNext(
            request = PermissionManagementRequest().apply {
                request = createUserRequest
            },
            respFuture = future
        )
        assertThrows<IllegalArgumentException> { future.getOrThrow() }
        verify(entityManager, never()).persist(any())
    }

    @Test
    fun `receiving CreateUserRequest specifying a parent group that exists persists a new user to the database`() {
        val group = mock<Group>()
        whenever(query.setParameter(any<String>(), any())).thenReturn(query)
        whenever(query.singleResult).thenReturn(0)
        whenever(entityManager.createQuery(any<String>())).thenReturn(query)
        whenever(entityManager.find(eq(Group::class.java), any())).thenReturn(group)
        val future = CompletableFuture<PermissionManagementResponse>()
        processor.onNext(
            request = PermissionManagementRequest().apply {
                request = createUserRequest.apply {
                    parentGroupId = "parent group"
                }
            },
            respFuture = future
        )
        assertTrue(future.getOrThrow().response is net.corda.data.permissions.User)
        verify(entityManager, times(1)).persist(any<User>())
    }

    @Test
    fun `receiving CreateUserRequest specifying a parent group that doesn't exist completes exceptionally`() {
        whenever(query.setParameter(any<String>(), any())).thenReturn(query)
        whenever(query.singleResult).thenReturn(0)
        whenever(entityManager.createQuery(any<String>())).thenReturn(query)
        whenever(entityManager.find(eq(Group::class.java), any())).thenReturn(null)
        val future = CompletableFuture<PermissionManagementResponse>()
        processor.onNext(
            request = PermissionManagementRequest().apply {
                request = createUserRequest.apply {
                    parentGroupId = "parent group"
                }
            },
            respFuture = future
        )
        assertThrows<IllegalArgumentException> { future.getOrThrow() }
        verify(entityManager, never()).persist(any())
    }
}