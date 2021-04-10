package net.cordax.flowworker.api.subscription.factory

import net.cordax.flowworker.api.processor.StateAndEventProcessor
import net.cordax.flowworker.api.processor.DurableProcessor
import net.cordax.flowworker.api.processor.PubSubProcessor
import net.cordax.flowworker.api.subscription.StateAndEventSubscription
import net.cordax.flowworker.api.subscription.Subscription
import java.util.concurrent.ExecutorService

interface SubscriptionFactory {

    fun <K,V> createPubSubSubscription(groupName:String,
                                       instanceId:Int,
                                       eventTopic:String,
                                       durableProcessor: PubSubProcessor<K, V>,
                                       executor: ExecutorService,
                                       properties: Map<String, String>): Subscription<K, V>

   fun <K, V> createDurableSubscription(groupName:String,
                                        instanceId:Int,
                                        eventTopic:String,
                                        durableProcessor: DurableProcessor<K, V>,
                                        properties: Map<String, String>) : Subscription<K, V>

   fun <K, S, E> createActorSubscription(groupName:String,
                                         instanceId:Int,
                                         eventTopic:String,
                                         checkpointTopic:String,
                                         processor: StateAndEventProcessor<K, S, E>,
                                         properties: Map<String, String>) : StateAndEventSubscription<K, S, E>
}