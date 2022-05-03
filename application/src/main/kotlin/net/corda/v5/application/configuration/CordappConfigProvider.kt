package net.corda.v5.application.configuration

import net.corda.v5.base.annotations.DoNotImplement

/**
 * Provides access to what the node knows about loaded applications.
 */
@DoNotImplement
interface CordappConfigProvider {

    /**
     * Exposes the current CorDapp configuration.
     *
     * @return The configuration for the current CorDapp.
     *
     * @throws IllegalStateException When called from a non-app context.
     */
    val appConfig: CordappConfig

}