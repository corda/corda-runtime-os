package net.corda.messaging.api.subscription.factory.config

/**
 * Class to store the required params to create a Subscription.
 * @property groupName The unique ID for a group of consumers.
 * @property eventTopic Topic to get events from.
 * @property instanceId Required for transactional publishing where order
 * and exactly once semantics are important. If null transactions are not used.
 * @property batchSize the number of records that will be processed each time.
 */
data class SubscriptionConfig (val groupName:String,
                               val eventTopic:String,
                               val instanceId:Int? = null,
                                val batchSize: Int = 100) {

    init {
        require(batchSize > 0) { "The batch size needs to be positive." }
    }

}
