package net.corda.membership.service.impl

import net.corda.data.membership.actions.request.DistributeGroupParameters
import net.corda.data.membership.actions.request.DistributeMemberInfo
import net.corda.data.membership.actions.request.MembershipActionsRequest
import net.corda.membership.service.impl.actions.DistributeGroupParametersActionHandler
import net.corda.membership.service.impl.actions.DistributeMemberInfoActionHandler
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MembershipActionsProcessorTest {
    private companion object {
        const val KEY = "KEY"
    }

    private val distributeMemberInfoActionHandler = Mockito.mockConstruction(DistributeMemberInfoActionHandler::class.java) { mock, _ ->
        whenever(mock.process(any(), any())) doReturn emptyList()
    }
    private val distributeGroupParametersActionHandler =
        Mockito.mockConstruction(DistributeGroupParametersActionHandler::class.java) { mock, _ ->
            whenever(mock.process(any(), any())) doReturn emptyList()
        }
    private val processor = MembershipActionsProcessor(mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock())

    @AfterEach
    fun cleanUp() {
        distributeMemberInfoActionHandler.close()
        distributeGroupParametersActionHandler.close()
    }

    @Test
    fun `processor forwards distribute member info request to DistributeMemberInfoAction`() {
        val distributeRequest = mock<DistributeMemberInfo>()

        processor.onNext(
            listOf(
                Record(
                    Schemas.Membership.MEMBERSHIP_ACTIONS_TOPIC,
                    KEY,
                    MembershipActionsRequest(distributeRequest)
                )
            )
        )

        verify(distributeMemberInfoActionHandler.constructed().last()).process(KEY, distributeRequest)
        verify(distributeGroupParametersActionHandler.constructed().last(), never()).process(any(), any())
    }

    @Test
    fun `processor forwards distribute group parameters request to DistributeGroupParametersAction`() {
        val distributeRequest = mock<DistributeGroupParameters>()

        processor.onNext(
            listOf(
                Record(
                    Schemas.Membership.MEMBERSHIP_ACTIONS_TOPIC,
                    KEY,
                    MembershipActionsRequest(distributeRequest)
                )
            )
        )

        verify(distributeGroupParametersActionHandler.constructed().last()).process(KEY, distributeRequest)
        verify(distributeMemberInfoActionHandler.constructed().last(), never()).process(any(), any())
    }

    @Test
    fun `processor returns an empty list if unknown action`() {
        val result = processor.onNext(
            listOf(
                Record(
                    Schemas.Membership.MEMBERSHIP_ACTIONS_TOPIC,
                    KEY,
                    MembershipActionsRequest(Unit),
                )
            )
        )

        verify(distributeMemberInfoActionHandler.constructed().last(), never()).process(any(), any())
        verify(distributeGroupParametersActionHandler.constructed().last(), never()).process(any(), any())
        assertThat(result).isEmpty()
    }
}
