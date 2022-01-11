package net.corda.messaging.api.subscription.config

/**
 * Class to store the required params to create a RPCConfiguration.
 *
 * @property groupName The unique ID for a group of consumers.
 * @property clientName The name of the client sending the request
 * @property requestTopic Topic to send events to
 * @property requestType The request type class
 * @property responseType The response type class
 *
 * The response topic is not present due to the pattern making use of the convention where the response topic
 * is called requestTopic.resp by default
 */
data class RPCConfig<REQUEST, RESPONSE>(
    val groupName: String,
    val clientName: String,
    val requestTopic: String,
    val requestType: Class<REQUEST>,
    val responseType: Class<RESPONSE>
)

inline fun <reified REQUEST, RESPONSE> RPCConfig<REQUEST, RESPONSE>.requestType(): Class<out Class<REQUEST>> = requestType::class.java

inline fun <reified REQUEST, RESPONSE> RPCConfig<REQUEST, RESPONSE>.responseType(): Class<out Class<RESPONSE>> = responseType::class.java
