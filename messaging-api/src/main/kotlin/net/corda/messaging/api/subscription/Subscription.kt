package net.corda.messaging.api.subscription


interface Subscription<K, V> : LifeCycle

interface StateAndEventSubscription<K, S, E> : LifeCycle
