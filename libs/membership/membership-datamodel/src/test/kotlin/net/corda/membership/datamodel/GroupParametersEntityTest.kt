package net.corda.membership.datamodel

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import javax.persistence.EntityManager
import javax.persistence.TypedQuery
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Order
import javax.persistence.criteria.Path
import javax.persistence.criteria.Root

class GroupParametersEntityTest {

    private val epoch = 9
    private val parameters = "123".toByteArray()
    private val entity = GroupParametersEntity(epoch, parameters)
    private val epochPath: Path<String> = mock()
    private val order: Order = mock()
    private val root: Root<GroupParametersEntity> = mock {
        on { get<String>(eq("epoch")) } doReturn epochPath
    }
    private val criteriaQuery: CriteriaQuery<GroupParametersEntity> = mock {
        on { from(eq(GroupParametersEntity::class.java)) } doReturn root
        on { select(eq(root)) } doReturn mock
        on { orderBy(eq(order)) } doReturn mock
    }
    private val criteriaBuilder: CriteriaBuilder = mock {
        on { createQuery(eq(GroupParametersEntity::class.java)) } doReturn criteriaQuery
        on { desc(epochPath) } doReturn order
    }
    private val typedQuery: TypedQuery<GroupParametersEntity> = mock {
        on { setMaxResults(any()) } doReturn mock
        on { resultList } doReturn listOf(entity)
    }
    private val em: EntityManager = mock {
        on { criteriaBuilder } doReturn criteriaBuilder
        on { createQuery(criteriaQuery) } doReturn typedQuery
    }

    @Nested
    inner class GetCurrentGroupParameters {
        @Test
        fun `successful execution when results exist`() {
            val result = em.getCurrentGroupParameters()

            assertThat(result).isNotNull.isEqualTo(entity)
            verify(em).criteriaBuilder
            verify(em).createQuery(criteriaQuery)
            verify(criteriaBuilder).createQuery(eq(GroupParametersEntity::class.java))
            verify(criteriaQuery).from(eq(GroupParametersEntity::class.java))
            verify(criteriaQuery).select(eq(root))
            verify(criteriaQuery).orderBy(eq(order))
            verify(criteriaBuilder).desc(eq(epochPath))
            verify(root).get<String>(eq("epoch"))
            verify(typedQuery).maxResults = eq(1)
            verify(typedQuery).resultList
        }

        @Test
        fun `successful execution when no results exist`() {
            whenever(typedQuery.resultList).doReturn(emptyList())

            val result = em.getCurrentGroupParameters()

            assertThat(result).isNull()

            verify(em).criteriaBuilder
            verify(em).createQuery(criteriaQuery)
            verify(criteriaBuilder).createQuery(eq(GroupParametersEntity::class.java))
            verify(criteriaQuery).from(eq(GroupParametersEntity::class.java))
            verify(criteriaQuery).select(eq(root))
            verify(criteriaQuery).orderBy(eq(order))
            verify(criteriaBuilder).desc(eq(epochPath))
            verify(root).get<String>(eq("epoch"))
            verify(typedQuery).maxResults = eq(1)
            verify(typedQuery).resultList

        }
    }
}