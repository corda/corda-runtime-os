package api.samples.subscription.factory

import api.samples.processor.ActorProcessor
import api.samples.processor.DurableProcessor
import api.samples.processor.PubSubProcessor
import api.samples.subscription.LifeCycle
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService

interface SubscriptionFactory {

    /**
     * Config Service can be injected so no need to pass as a param.
     * Properties map is additional properties not from the config service,
     */

    fun <K,V> createPubSubSubscription(groupName:String,
                                       instanceId:Int,
                                       eventTopic:String,
                                       durableProcessor: PubSubProcessor<K, V>,
                                       executor: ExecutorService,
                                       properties: Map<String, String>): LifeCycle

   fun <K, V> createDurableSubscription(groupName:String,
                                        instanceId:Int,
                                        eventTopic:String,
                                        durableProcessor: DurableProcessor<K, V>,
                                        properties: Map<String, String>) : LifeCycle

   fun <K, S, E> createActorSubscription(groupName:String,
                               instanceId:Int,
                               eventTopic:String,
                               checkpointTopic:String,
                               processor: ActorProcessor<K, S, E>,
                               properties: Map<String, String>) : LifeCycle
}