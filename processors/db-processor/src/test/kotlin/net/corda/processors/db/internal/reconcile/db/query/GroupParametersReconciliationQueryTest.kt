package net.corda.processors.db.internal.reconcile.db.query

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.membership.datamodel.GroupParametersEntity
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.toMap
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.GroupParameters
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.TypedQuery
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Order
import javax.persistence.criteria.Path
import javax.persistence.criteria.Root

class GroupParametersReconciliationQueryTest {
    private val epochKey = "epoch"
    private val epochValue = 5
    private val serializedParams = "group-params".toByteArray()

    private val deserializedParams = KeyValuePairList(
        listOf(KeyValuePair(epochKey, epochValue.toString()))
    )
    private val groupParametersEntity = GroupParametersEntity(epochValue, serializedParams)

    private val cordaAvroDeserializer: CordaAvroDeserializer<KeyValuePairList> = mock {
        on { deserialize(eq(serializedParams)) } doReturn deserializedParams
    }
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory = mock {
        on { createAvroDeserializer<KeyValuePairList>(any(), any()) } doReturn cordaAvroDeserializer
    }

    private val groupParamsMapCaptor = argumentCaptor<KeyValuePairList>()
    private val groupParameters: GroupParameters = mock()
    private val groupParametersFactory: GroupParametersFactory = mock {
        on { create(groupParamsMapCaptor.capture()) } doReturn groupParameters
    }

    private val groupParametersReconciliationQuery = GroupParametersReconciliationQuery(
        cordaAvroSerializationFactory,
        groupParametersFactory
    )

    private val holdingIdentity = HoldingIdentity(
        MemberX500Name.parse("O=Alice, L=London, C=GB"),
        UUID(0, 1).toString()
    )
    private val virtualNodeInfo = VirtualNodeInfo(
        holdingIdentity,
        CpiIdentifier(
            "my-cpi",
            "1.0",
            null
        ),
        vaultDmlConnectionId = UUID(0, 1),
        cryptoDmlConnectionId = UUID(0, 1),
        uniquenessDmlConnectionId = UUID(0, 1),
        timestamp = Instant.ofEpochSecond(1)
    )

    private val maxResultsCaptor = argumentCaptor<Int>()
    private val typedQuery: TypedQuery<GroupParametersEntity> = mock {
        on { setMaxResults(maxResultsCaptor.capture()) } doReturn mock
        on { singleResult } doReturn groupParametersEntity
    }
    private val epochPath: Path<String> = mock()
    private val order: Order = mock()
    private val root: Root<GroupParametersEntity> = mock {
        on { get<String>(eq(epochKey)) } doReturn epochPath
    }
    private val criteriaQuery: CriteriaQuery<GroupParametersEntity> = mock {
        on { from(eq(GroupParametersEntity::class.java)) } doReturn root
        on { select(eq(root)) } doReturn mock
        on { orderBy(eq(order)) } doReturn mock
    }
    private val criteriaBuilder: CriteriaBuilder = mock {
        on { createQuery(eq(GroupParametersEntity::class.java)) } doReturn criteriaQuery
        on { desc(eq(epochPath)) } doReturn order
    }
    private val entityManager: EntityManager = mock {
        on { criteriaBuilder } doReturn criteriaBuilder
        on { createQuery(eq(criteriaQuery)) } doReturn typedQuery
    }

    @Test
    fun `Resulting list from invoking the query is as expected`() {
        val result = assertDoesNotThrow {
            groupParametersReconciliationQuery.invoke(virtualNodeInfo, entityManager)
        }

        assertThat(result).hasSize(1)
        val versionedRecord = result.first()

        assertThat(versionedRecord.version).isEqualTo(epochValue)
        assertThat(versionedRecord.isDeleted).isFalse
        assertThat(versionedRecord.key).isEqualTo(holdingIdentity)
        assertThat(versionedRecord.value).isEqualTo(groupParameters)
    }

    @Test
    fun `Query only requests one record from the database`() {
        groupParametersReconciliationQuery.invoke(virtualNodeInfo, entityManager)

        assertThat(maxResultsCaptor.firstValue).isEqualTo(1)
    }

    @Test
    fun `Full list of parameters are included when creating the group parameters object`() {
        groupParametersReconciliationQuery.invoke(virtualNodeInfo, entityManager)

        assertThat(groupParamsMapCaptor.firstValue.toMap())
            .hasSize(1)
            .containsOnlyKeys(epochKey)
            .containsEntry(epochKey, epochValue.toString())
    }

}