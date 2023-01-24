package net.corda.httprpc.client

import net.corda.httprpc.RestResource
import net.corda.v5.base.annotations.DoNotImplement

/**
 * Represents a logical connection to the HTTP RPC server which may go up and down.
 * The [proxy] object can be used to make remote calls using the interface methods.
 */
@DoNotImplement
interface HttpRpcConnection<out I : RestResource> {
    val proxy: I
    val serverProtocolVersion: Int
}