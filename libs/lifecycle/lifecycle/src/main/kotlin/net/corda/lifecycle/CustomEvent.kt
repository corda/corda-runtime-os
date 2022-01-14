package net.corda.lifecycle

class CustomEvent(val registration: RegistrationHandle, val payload: Any): LifecycleEvent