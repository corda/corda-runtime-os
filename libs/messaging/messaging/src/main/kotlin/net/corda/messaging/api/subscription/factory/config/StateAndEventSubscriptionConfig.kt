package net.corda.messaging.api.subscription.factory.config

/**
 * Class to store the required params to create a StateAndEventSubscription.
 * @property groupName The unique ID for a group of consumers.
 * @property instanceId Required for transactional publishing where order and exactly once semantics are important.
 * @property stateTopic Topic to get state for a given event.
 * @property eventTopic Topic to get events from.
 */
data class StateAndEventSubscriptionConfig (val groupName:String,
                               val instanceId:Int,
                               val stateTopic:String,
                               val eventTopic:String)