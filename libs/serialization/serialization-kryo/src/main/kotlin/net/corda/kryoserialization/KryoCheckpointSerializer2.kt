package net.corda.kryoserialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.util.Pool
import net.corda.kryoserialization.impl.MyKryo
import net.corda.serialization.checkpoint.CheckpointSerializer
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream

class KryoCheckpointSerializer2(
    private val kryoPool: Pool<MyKryo>,
) : CheckpointSerializer, KryoCheckpointSerializer {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun <T : Any> deserialize(
        bytes: ByteArray,
        clazz: Class<T>,
    ): T {
        return try {
            kryoInput(ByteArrayInputStream(bytes)) {
                @Suppress("unchecked_cast")
                kryoPool.obtain().let { kryo ->
                    try {
                        log.error("USING KRYO : $kryo")
                        kryo.kryo.readClassAndObject(this) as T
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
            kryoOutput {
                kryoPool.obtain().let { kryo ->
                    try {
                        log.error("USING KRYO : $kryo")
                        kryo.kryo.writeClassAndObject(this, obj)
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

