package net.corda.interop.service

import net.corda.interop.service.impl.HardcodedFacadeToFlowMapperServiceImpl
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class HardcodedFacadeToFlowMapperServiceImplTest {

    companion object {
        val ALICE_ALTER_EGO_X500 = "CN=Alice Alter Ego, O=Alice Alter Ego Corp, L=LDN, C=GB"
        val ALICE_ALTER_EGO_X500_NAME = MemberX500Name.parse(ALICE_ALTER_EGO_X500)
    }

    lateinit var facadeToFlowMapperService: HardcodedFacadeToFlowMapperServiceImpl
    @BeforeEach
    internal fun setUp() {
        facadeToFlowMapperService = HardcodedFacadeToFlowMapperServiceImpl()
    }

    @Test
    fun verifyGetFlowNameReturnTheFlowNameFromDummyFileIfVNodeInfoIsNotPresent() {
        //given
        val holdingIdentity = HoldingIdentity(ALICE_ALTER_EGO_X500_NAME, "test-group")
        val facadeId = "org.corda.interop/platform/hello-interop/v1.0"
        val facadeMethod = "say-hello"

        //when
        val flowName = facadeToFlowMapperService.getFlowName(holdingIdentity, facadeId, facadeMethod)

        //then
        Assertions.assertEquals("com.r3.corda.testing.interop.FacadeInvocationResponderFlow", flowName)
    }
}