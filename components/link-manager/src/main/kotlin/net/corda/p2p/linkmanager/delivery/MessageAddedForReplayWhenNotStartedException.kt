package net.corda.p2p.linkmanager.delivery

class MessageAddedForReplayWhenNotStartedException(component: String):
    IllegalStateException("A message was added for replay before the $component was started.")
