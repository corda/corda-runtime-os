package net.corda.interop.service

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock


internal class FacadeToFlowMapperServiceTest {

    companion object {
        val ALICE_ALTER_EGO_X500 = "CN=Alice Alter Ego, O=Alice Alter Ego Corp, L=LDN, C=GB"
        val ALICE_ALTER_EGO_X500_NAME = MemberX500Name.parse(ALICE_ALTER_EGO_X500)
    }

    lateinit var facadeToFlowMapperService: FacadeToFlowMapperService
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService>()
    private val cpiInfoReadService = mock<CpiInfoReadService>()
    @BeforeEach
    internal fun setUp() {
        facadeToFlowMapperService = FacadeToFlowMapperService(virtualNodeInfoReadService, cpiInfoReadService)
    }

    @Test
    fun verifyGetFlowNameReturnTheFlowNameFromDummyFileIfVNodeInfoIsNotPresent() {
        //given
        val holdingIdentity = HoldingIdentity(ALICE_ALTER_EGO_X500_NAME, "test-group")
        val facadeId = "org.corda.interop/platform/hello-interop/v1.0"
        val facadeMethod = "say-hello"

        //when
        val flowName = facadeToFlowMapperService.getFlowName( holdingIdentity, facadeId, facadeMethod)

        //then
        Assertions.assertEquals(flowName, "com.net.corda.flow.SayHelloFlow")
    }
}