package net.corda.messaging.api.subscription.config

/**
 * Class to store the required params to create a Subscription.
 *
 * @property groupName The unique ID for a group of consumers.
 * @property eventTopic Topic to get events from.
 * @property instanceId Required for transactional publishing. Id must be unique across worker instances
 * and subscription instances within the same consumer group. If null transactions are not used.
 */
data class SubscriptionConfig (val groupName:String,
                               val eventTopic:String,
                               val instanceId:Int? = null)
