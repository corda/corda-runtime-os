package api.samples.subscription


interface Subscription<K, V> :LifeCycle

interface StateAndEventSubscription<K, S, E> :LifeCycle
