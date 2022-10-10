package net.corda.membership.impl.registration.dynamic.verifiers

import net.corda.membership.lib.MemberInfoExtension
import java.net.URI

internal class P2pEndpointVerifier(
    private val orderVerifier: OrderVerifier = OrderVerifier()
) {
    fun verifyContext(context: Map<String, String>) {
        val urlEntries = context.entries.filter { (key, _) ->
            MemberInfoExtension.URL_KEY.format("[0-9]+").toRegex().matches(key)
        }
        urlEntries.map { it.key }.apply {
            require(isNotEmpty()) { "No endpoint URL was provided." }
            require(orderVerifier.isOrdered(this, 2)) { "Provided endpoint URLs are incorrectly numbered." }
        }
        context.keys.filter { MemberInfoExtension.PROTOCOL_VERSION.format("[0-9]+").toRegex().matches(it) }.apply {
            require(isNotEmpty()) { "No endpoint protocol was provided." }
            require(orderVerifier.isOrdered(this, 2)) { "Provided endpoint protocols are incorrectly numbered." }
        }
        urlEntries.map { it.value }.forEach {
            verifyP2pUrl(it)
        }
    }

    private fun verifyP2pUrl(url: String) {
        val uri = URI.create(url)
        require(uri.scheme == "https") { "Endpoint URL must be https" }
        require(uri.port > 0) { "Endpoint URL must have an explicit port" }
        require(uri.host != null) { "Endpoint URL must have an explicit host" }
        require(uri.userInfo == null) { "Endpoint URL must not have an authentication info" }
    }
}
