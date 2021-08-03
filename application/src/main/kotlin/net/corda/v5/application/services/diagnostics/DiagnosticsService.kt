package net.corda.v5.application.services.diagnostics

import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.application.injection.CordaServiceInjectable
import net.corda.v5.base.annotations.DoNotImplement

/**
 * A [DiagnosticsService] provides APIs that allow CorDapps to query information about the node that CorDapp is currently running on.
 */
@DoNotImplement
interface DiagnosticsService : CordaServiceInjectable, CordaFlowInjectable {

    /**
     * Retrieve information about the current node version.
     *
     * @return The [NodeVersionInfo] holding information about the current node version.
     */
    val nodeVersionInfo : NodeVersionInfo
}