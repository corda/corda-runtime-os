package net.corda.messaging.api.subscription.config

/**
 * Class to store the required params to create a Subscription.
 *
 * @property groupName The unique ID for a group of consumers.
 * @property eventTopic Topic to get events from.
 */
data class SubscriptionConfig (val groupName:String,
                               val eventTopic:String)
