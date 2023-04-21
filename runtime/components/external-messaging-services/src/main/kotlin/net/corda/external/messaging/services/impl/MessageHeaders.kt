package net.corda.external.messaging.services.impl

class MessageHeaders private constructor(){
    companion object{
        const val HOLDING_ID = "HOLDING_ID"
        const val CHANNEL_NAME = "CHANNEL_NAME"
        const val CORRELATION_ID = "CORRELATION_ID"
        const val CREATION_TIME_UTC = "CREATION_TIME_UTC"
    }
}
