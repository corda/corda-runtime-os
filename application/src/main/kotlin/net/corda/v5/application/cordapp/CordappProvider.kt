package net.corda.v5.application.cordapp

import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.application.injection.CordaServiceInjectable
import net.corda.v5.base.annotations.DoNotImplement

/**
 * Provides access to what the node knows about loaded applications.
 */
@DoNotImplement
interface CordappProvider : CordaServiceInjectable, CordaFlowInjectable {

    /**
     * Exposes the current CorDapp configuration.
     *
     * The calling application is found via stack walking and finding the first class on the stack that matches any class contained within
     * the automatically resolved [Cordapp]s loaded by the [net.corda.nodeapi.internal.cordapp.CordappLoader]
     *
     * @return The configuration for the current CorDapp.
     *
     * @throws IllegalStateException When called from a non-app context.
     */
    val appConfig: CordappConfig

}