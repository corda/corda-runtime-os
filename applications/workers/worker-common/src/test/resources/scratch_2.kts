
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

val random = Random(System.nanoTime())

data class InMessage(val id: Int, val payload: String, var channelId: Int = -1)
data class OutMessage(val id: Int, val payload: String)

val processingChannels = listOf<Channel<InMessage>>(
    Channel(100),
    Channel(100),
    Channel(100),
)
val sendingChannel = Channel<OutMessage>(1000)

fun CoroutineScope.produceMessages(): ReceiveChannel<InMessage> = produce {
    var x = 1
    while (true) {
        send(InMessage(random.nextInt(100), "In message ${x++}"))
        delay(100)
    }
}

fun CoroutineScope.messageFanOut(msgs: ReceiveChannel<InMessage>) = launch {
    // use hash of msg id to define which channel this goes into to guarantee messages with the same ID going out-of-order
    for(msg in msgs) {
        val channelId = msg.id.mod(processingChannels.size)
        processingChannels[channelId].send(msg.also { it.channelId = channelId })
    }
}

fun CoroutineScope.messageProcessor(id: Int, msgs: ReceiveChannel<InMessage>) = launch {
    for (msg in msgs) {
        println("Processor #$id is processing $msg")
        sendingChannel.send(OutMessage(random.nextInt(1000,2000), "Processor #$id processed ${msg.id} on ${Thread.currentThread().name}"))
        delay(random.nextLong(0,1000))
    }
}

fun CoroutineScope.messageSender(msgs: Channel<OutMessage>) = launch {
    var beginTx = 0L
    var txStarted = false
    for (msg in msgs) {
        if(!txStarted) {
            println("Begin tx")
            beginTx = System.nanoTime()
            txStarted = true
        }
        else if(System.nanoTime() - beginTx > 1_000_000 * 500) {
            println("Commit tx")
            txStarted = false
        }
        println("Sending $msg")
    }
    if(txStarted) println("Commit tx")
}


runBlocking<Unit> {
    val msgs = produceMessages()
    messageSender(sendingChannel)
    messageFanOut(msgs)
    for(x in processingChannels.indices) {
        messageProcessor(x, processingChannels[x])
    }

    delay(5000)
    coroutineContext.cancelChildren()
}
