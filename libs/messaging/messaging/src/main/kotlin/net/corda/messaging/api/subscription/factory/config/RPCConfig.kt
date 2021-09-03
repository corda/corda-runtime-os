package net.corda.messaging.api.subscription.factory.config

/**
 * Class to store the required params to create a RPCConfiguration.
 *
 * @property groupName The unique ID for a group of consumers.
 * @property clientName The name of the client sending the request
 * @property requestTopic Topic to send events to
 * @property requestType The request type class
 * @property responseType The response type class
 */
data class RPCConfig<TREQ, TRESP>(
    val groupName: String,
    val clientName: String,
    val requestTopic: String,
    val requestType: Class<TREQ>,
    val responseType: Class<TRESP>
)