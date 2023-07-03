package net.corda.interop.web3j.internal

import java.io.InputStream
import net.corda.v5.base.annotations.Suspendable
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.http.HttpService

fun Transaction.render() =
    "$from/$to--$data"

fun Request<*, *>.render() =
    "($method[${(params[0] as Transaction).render()}]);$id;${responseType.canonicalName}"

class DelegatingWeb3JService: HttpService(false) {
    override fun close() {
        TODO("Not yet implemented")
    }

    @Suspendable
    override fun performIO(payload: String): InputStream {
        println("Sending payload: $payload")
        return super.performIO(payload)
    }
}