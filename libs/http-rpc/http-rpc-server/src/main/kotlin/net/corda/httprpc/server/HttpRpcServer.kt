package net.corda.httprpc.server

import net.corda.lifecycle.Resource

interface HttpRpcServer : Resource {
    fun start()

    val port: Int
}
