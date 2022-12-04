package net.corda.virtualnode.async.operation.events

import net.corda.lifecycle.LifecycleEvent

class SnapshotReceived : LifecycleEvent
class StatusReceived : LifecycleEvent
class ErrorReceived : LifecycleEvent