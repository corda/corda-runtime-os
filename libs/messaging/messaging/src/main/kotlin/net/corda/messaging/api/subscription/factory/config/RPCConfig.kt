package net.corda.messaging.api.subscription.factory.config

/**
 * Class to store the required params to create a RPCConfiguration.
 *
 * @property groupName The unique ID for a group of consumers.
 * @property clientName The name of the client sending the request
 * @property eventTopic Topic to send events to
 */
data class RPCConfig (val groupName:String,
                      val clientName:String,
                      val eventTopic: String)