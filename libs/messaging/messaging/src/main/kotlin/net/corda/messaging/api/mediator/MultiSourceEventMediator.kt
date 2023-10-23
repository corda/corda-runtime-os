package net.corda.messaging.api.mediator

import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.subscription.SubscriptionBase

/**
 * Multi-source event mediator is used to consume messages from multiple sources using [MediatorConsumer]s,
 * process them using [StateAndEventProcessor] to generate output messages that are then sent to [MessagingClient]s.
 *
 * @param K Type of event key.
 * @param S Type of event state.
 * @param E Type of event.
 */
interface MultiSourceEventMediator<K, S, E> : SubscriptionBase