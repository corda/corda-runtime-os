package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.kryoserialization.readBytesWithLength
import java.io.InputStream

// This is a temporary inefficient serializer for sending InputStreams through RPC. This may be done much more
// efficiently using Artemis's large message feature.
object InputStreamSerializer : Serializer<InputStream>() {
    override fun write(kryo: Kryo, output: Output, stream: InputStream) {
        val buffer = ByteArray(4096)
        while (true) {
            val numberOfBytesRead = stream.read(buffer)
            if (numberOfBytesRead != -1) {
                output.writeInt(numberOfBytesRead, true)
                output.writeBytes(buffer, 0, numberOfBytesRead)
            } else {
                output.writeInt(0, true)
                break
            }
        }
    }

    override fun read(kryo: Kryo, input: Input, type: Class<InputStream>): InputStream {
        val chunks = ArrayList<ByteArray>()
        while (true) {
            val chunk = input.readBytesWithLength()
            if (chunk.isEmpty()) {
                break
            } else {
                chunks.add(chunk)
            }
        }
        val flattened = ByteArray(chunks.sumBy { it.size })
        var offset = 0
        for (chunk in chunks) {
            System.arraycopy(chunk, 0, flattened, offset, chunk.size)
            offset += chunk.size
        }
        return flattened.inputStream()
    }

}
