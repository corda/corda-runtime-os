package net.cordapp.testing.chatframework

import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.base.types.MemberX500Name
import org.mockito.kotlin.whenever

/**
 * Mock a virtual node name for this FlowEngine mock. Any Flows asking their FlowEngine mock for their virtual node
 * name will receive this. It is recommended all FlowEngine mocks have this method called on them when a FlowMockHelper
 * is built as virtual node names are a fundamental facet of the FlowEngine api used by many Flows. E.g.:
 * <pre>
 *     val flowMockHelper = FlowMockHelper {
 *         mockService<FlowEngine>().withVirtualNodeName(X500_NAME)
 *     }
 * </pre>
 */
fun FlowEngine.withVirtualNodeName(name: String): FlowEngine {
    whenever(this.virtualNodeName).thenReturn(MemberX500Name.parse(name))
    return this
}
