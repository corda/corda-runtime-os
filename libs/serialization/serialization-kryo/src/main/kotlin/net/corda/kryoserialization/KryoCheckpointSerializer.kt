package net.corda.kryoserialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.util.Pool
import net.corda.internal.serialization.encoding.Encoder
import net.corda.internal.serialization.encoding.EncoderService
import net.corda.internal.serialization.encoding.EncoderServiceFactory
import net.corda.internal.serialization.encoding.EncoderType
import net.corda.serialization.checkpoint.CheckpointSerializer
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream

class KryoCheckpointSerializer(
    private val kryoPool: Pool<Kryo>,
    streamEncoderServiceFactory: EncoderService = EncoderServiceFactory(),
    streamEncoderType: EncoderType = EncoderType.SNAPPY
) : CheckpointSerializer {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    // The encoders are thread safe as they create new streams each time
    private val encoder: Encoder = streamEncoderServiceFactory.get(streamEncoderType)

    override fun <T : Any> deserialize(
        bytes: ByteArray,
        clazz: Class<T>,
    ): T {
        return try {
            kryoInput(encoder.decompress(ByteArrayInputStream(bytes))) {
                @Suppress("unchecked_cast")
                kryoPool.obtain().let { kryo ->
                    try {
                        kryo.readClassAndObject(this) as T
                    } finally {
                        kryoPool.free(kryo)
                    }
                }
            }
        } catch (ex: Exception) {
            log.error("Failed to deserialize bytes", ex)
            throw ex
        }
    }

    override fun <T : Any> serialize(obj: T): ByteArray {
        return try {
            kryoOutput(encoder) {
                kryoPool.obtain().let { kryo ->
                    try {
                        kryo.writeClassAndObject(this, obj)
                    } finally {
                        kryoPool.free(kryo)
                    }
                }
            }
        } catch (ex: Exception) {
            log.error("Failed to serialize", ex)
            throw ex
        }
    }
}

