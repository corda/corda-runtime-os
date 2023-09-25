package net.corda.membership.impl.registration.dynamic.mgm

import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceOperation
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.messaging.api.records.Record
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class MGMRegistrationGroupPolicyHandlerTest {
    private val testHoldingIdentity = HoldingIdentity(
        MemberX500Name.parse("O=Alice, L=Dublin, C=IE"),
        UUID(0, 1).toString()
    )

    private val groupPolicyContextCaptor = argumentCaptor<Map<String, String>>()
    private val groupPolicyContext
        get() = assertDoesNotThrow(groupPolicyContextCaptor::firstValue)

    private val mockLayeredPropertyMap: LayeredPropertyMap = mock()
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory = mock {
        on { createMap(groupPolicyContextCaptor.capture()) } doReturn mockLayeredPropertyMap
    }
    private class Operation<T>(
        private val value: MembershipPersistenceResult<T>
    ) : MembershipPersistenceOperation<T> {
        override fun execute() = value

        override fun createAsyncCommands(): Collection<Record<*, *>> {
            return emptyList()
        }
    }
    private val membershipPersistenceClient: MembershipPersistenceClient = mock {
        on {
            persistGroupPolicy(eq(testHoldingIdentity), eq(mockLayeredPropertyMap), any())
        } doReturn Operation(MembershipPersistenceResult.success())

        on {
            persistGroupParametersInitialSnapshot(eq(testHoldingIdentity))
        } doReturn Operation(MembershipPersistenceResult.Success(mock()))
    }

    private val testContext: Map<String, String> = mapOf(
        REGISTRATION_PROTOCOL to "valid protocol",
        SESSION_KEY_IDS.format(0) to "non group policy property"
    )

    private val membershipQueryClient = mock<MembershipQueryClient>()

    private val mgmRegistrationGroupPolicyHandler = MGMRegistrationGroupPolicyHandler(
        layeredPropertyMapFactory,
        membershipPersistenceClient,
        membershipQueryClient,
    )

    @Test
    fun `non group parameters are properly are filtered out of the context and the group policy prefix was removed`() {
        assertThat(testContext).hasSize(2).withFailMessage(
            "Test map is not as expected before testing. " +
                    "Expected size 2 in order to verify results correctly."
        )

        mgmRegistrationGroupPolicyHandler.buildAndPersist(testHoldingIdentity, testContext)

        verify(layeredPropertyMapFactory).createMap(any())
        assertThat(groupPolicyContext)
            .hasSize(1)
            .containsOnlyKeys(REGISTRATION_PROTOCOL.removePrefix(GROUP_POLICY_PREFIX_WITH_DOT))
    }

    @Test
    fun `group policy is persisted using the correct holding identity and map`() {
        mgmRegistrationGroupPolicyHandler.buildAndPersist(testHoldingIdentity, testContext)

        verify(membershipPersistenceClient).persistGroupPolicy(
            eq(testHoldingIdentity),
            eq(mockLayeredPropertyMap),
            eq(1)
        )
    }

    @Test
    fun `Failed group policy persistence is rethrown as group policy handling exception`() {
        whenever (
            membershipPersistenceClient.persistGroupPolicy(any(), any(), anyLong())
        ) doReturn Operation(MembershipPersistenceResult.Failure(""))

        assertThrows<MGMRegistrationGroupPolicyHandlingException> {
            mgmRegistrationGroupPolicyHandler.buildAndPersist(testHoldingIdentity, testContext)
        }
        verify(membershipPersistenceClient).persistGroupPolicy(any(), any(), eq(1))
    }

    @Test
    fun `Expected group policy object is returned`() {
        val output = mgmRegistrationGroupPolicyHandler.buildAndPersist(testHoldingIdentity, testContext)

        assertThat(output)
            .isEqualTo(mockLayeredPropertyMap)
    }

    @Test
    fun `retrieving group policy is successful`() {
        val groupPolicy = mock<LayeredPropertyMap>()
        whenever(membershipQueryClient.queryGroupPolicy(testHoldingIdentity))
            .thenReturn(MembershipQueryResult.Success(Pair(groupPolicy, 1)))

        assertThat(mgmRegistrationGroupPolicyHandler.getLastGroupPolicy(testHoldingIdentity)).isEqualTo(groupPolicy)
    }

    @Test
    fun `retrieving group policy fails and gets propagated`() {
        val errorMsg = "Test failed!"
        whenever(membershipQueryClient.queryGroupPolicy(testHoldingIdentity))
            .thenReturn(MembershipQueryResult.Failure(errorMsg))

        val message = assertThrows<MGMRegistrationGroupPolicyHandlingException> {
            mgmRegistrationGroupPolicyHandler.getLastGroupPolicy(testHoldingIdentity)
        }.message
        assertThat(message).contains(errorMsg)
    }
}