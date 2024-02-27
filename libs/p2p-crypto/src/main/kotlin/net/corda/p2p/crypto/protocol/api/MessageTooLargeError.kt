package net.corda.p2p.crypto.protocol.api

import net.corda.v5.base.exceptions.CordaRuntimeException

class MessageTooLargeError(messageSize: Int, maxMessageSize: Int) :
    CordaRuntimeException("Message's size ($messageSize bytes) was larger than the max message size of the session ($maxMessageSize bytes)")
