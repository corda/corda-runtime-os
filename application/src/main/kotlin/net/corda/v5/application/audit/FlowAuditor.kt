package net.corda.v5.application.audit

import net.corda.v5.application.flows.exceptions.FlowException
import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable

@DoNotImplement
interface FlowAuditor : CordaFlowInjectable {
    /**
     * Flows can call this method to ensure that the active FlowInitiator is authorised for a particular action.
     * This provides fine-grained control over application level permissions, when RPC control over starting the flow is insufficient,
     * or the permission is runtime dependent upon the choices made inside long lived flow code.
     * For example some users may have restricted limits on how much cash they can transfer, or whether they can change certain fields.
     * An audit event is always recorded whenever this method is used.
     * If the permission is not granted for the FlowInitiator a FlowException is thrown.
     * @param permissionName is a string representing the desired permission. Each flow is given a distinct namespace for these permissions.
     * @param extraAuditData in the audit log for this permission check these extra key value pairs will be recorded.
     */
    @Suspendable
    @Throws(FlowException::class)
    fun checkFlowPermission(permissionName: String, extraAuditData: Map<String, String>)

    /**
     * Flows can call this method to record application level flow audit events
     * @param eventType is a string representing the type of event. Each flow is given a distinct namespace for these names.
     * @param comment a general human readable summary of the event.
     * @param extraAuditData in the audit log for this permission check these extra key value pairs will be recorded.
     */
    @Suspendable
    fun recordAuditEvent(eventType: String, comment: String, extraAuditData: Map<String, String>)
}

