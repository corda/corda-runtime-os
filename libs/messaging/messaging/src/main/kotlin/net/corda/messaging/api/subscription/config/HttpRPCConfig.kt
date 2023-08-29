package net.corda.messaging.api.subscription.config

data class HttpRPCConfig<REQUEST, RESPONSE>(
    val groupName: String,
    val clientName: String,
    val endpoint: String,
    val requestType: Class<REQUEST>,
    val responseType: Class<RESPONSE>
)

inline fun <reified REQUEST, RESPONSE> HttpRPCConfig<REQUEST, RESPONSE>.requestType(): Class<out Class<REQUEST>>
    = requestType::class.java

inline fun <reified REQUEST, RESPONSE> HttpRPCConfig<REQUEST, RESPONSE>.responseType(): Class<out Class<RESPONSE>>
    = responseType::class.java