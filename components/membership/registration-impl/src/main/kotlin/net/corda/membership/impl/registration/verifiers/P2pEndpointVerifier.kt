package net.corda.membership.impl.registration.verifiers

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
        val urls = urlEntries.map { it.value }
        urls.forEach {
            verifyP2pUrl(it)
        }
        verifyUniqueUrls(urls)
    }

    private fun verifyP2pUrl(url: String) {
        val uri = try {
            URI.create(url)
        } catch (e: java.lang.IllegalArgumentException) {
            throw IllegalArgumentException("Endpoint URL ('$url') is not a valid URL.")
        }

        require(uri.scheme == "https") { "The scheme of the endpoint URL ('$url') was not https." }
        require(uri.host != null) { "The host of the endpoint URL ('$url') was not specified or had an invalid value." }
        require(uri.port > 0) { "The port of the endpoint URL ('$url') was not specified or had an invalid value." }
        require(uri.userInfo == null) { "Endpoint URL ('$url') had user info specified, which must not be specified." }
    }

    private fun verifyUniqueUrls(urls: List<String>) {
        val nonUniqueUrls = urls.map { URI.create(it) }
            .groupingBy { it }
            .eachCount()
            .filter { it.value > 1 }
            .keys

        require(nonUniqueUrls.isEmpty()) {
            "Duplicate connection URLs found: $nonUniqueUrls"
        }
    }
}
