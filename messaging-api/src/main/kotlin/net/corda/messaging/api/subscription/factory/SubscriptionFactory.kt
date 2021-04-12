package net.corda.messaging.api.subscription.factory

import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.Subscription
import java.util.concurrent.ExecutorService

interface SubscriptionFactory {

    fun <K,V> createPubSubSubscription(groupName:String,
                                       instanceId:Int,
                                       eventTopic:String,
                                       processor: PubSubProcessor<K, V>,
                                       executor: ExecutorService,
                                       properties: Map<String, String>): Subscription<K, V>

   fun <K, V> createDurableSubscription(groupName:String,
                                        instanceId:Int,
                                        eventTopic:String,
                                        processor: DurableProcessor<K, V>,
                                        properties: Map<String, String>) : Subscription<K, V>

   fun <K, S, E> createActorSubscription(groupName:String,
                                         instanceId:Int,
                                         eventTopic:String,
                                         stateTopic:String,
                                         processor: StateAndEventProcessor<K, S, E>,
                                         properties: Map<String, String>) : StateAndEventSubscription<K, S, E>
}