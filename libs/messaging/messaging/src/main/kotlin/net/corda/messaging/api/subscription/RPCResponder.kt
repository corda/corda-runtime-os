package net.corda.messaging.api.subscription

import net.corda.lifecycle.Lifecycle

interface RPCResponder<TREQ, TRESP> : Lifecycle {

    /**
     * Send response via RPC
     */
    fun sendResponse(req: TRESP)

}