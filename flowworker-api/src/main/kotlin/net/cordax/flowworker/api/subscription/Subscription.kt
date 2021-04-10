package net.cordax.flowworker.api.subscription


interface Subscription<K, V> : LifeCycle

interface StateAndEventSubscription<K, S, E> : LifeCycle
