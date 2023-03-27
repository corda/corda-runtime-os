package net.corda.membership.service.impl

import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.actions.request.DistributeMemberInfo
import net.corda.data.membership.actions.request.MembershipActionsRequest
import net.corda.membership.service.impl.actions.DistributeMemberInfoAction
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MembershipActionsProcessorTest {
    private companion object {
        const val KEY = "KEY"
    }

    private val distributeMemberInfoAction = Mockito.mockConstruction(DistributeMemberInfoAction::class.java) { mock, _ ->
        whenever(mock.process(any(), any())) doReturn emptyList()
    }
    private val processor = MembershipActionsProcessor(mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock())

    @AfterEach
    fun cleanUp() {
        distributeMemberInfoAction.close()
    }

    @Test
    fun `processor forwards distribute member info request to DistributeMemberInfoAction`() {
        val distributeRequest = mock<DistributeMemberInfo>()

        processor.onNext(listOf(Record(
            Schemas.Membership.MEMBERSHIP_ACTIONS_TOPIC,
            KEY,
            MembershipActionsRequest(distributeRequest)
        )))

        verify(distributeMemberInfoAction.constructed().last()).process(KEY, distributeRequest)
    }
}