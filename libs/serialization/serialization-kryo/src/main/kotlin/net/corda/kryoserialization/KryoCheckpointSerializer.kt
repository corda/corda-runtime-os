package net.corda.kryoserialization

import com.esotericsoftware.kryo.Kryo
import net.corda.serialization.checkpoint.CheckpointSerializer
import net.corda.v5.base.util.uncheckedCast
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream

class KryoCheckpointSerializer(
    private val kryo: Kryo,
) : CheckpointSerializer {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun <T : Any> deserialize(
        bytes: ByteArray,
        clazz: Class<T>,
    ): T {
        return try {
            kryoInput(ByteArrayInputStream(bytes)) {
                uncheckedCast(kryo.readClassAndObject(this))
            }
        } catch (ex: Exception) {
            log.error("Failed to deserialize bytes", ex)
            throw ex
        }
    }

    override fun <T : Any> serialize(obj: T): ByteArray {
        return try {
            kryoOutput {
                kryo.writeClassAndObject(this, obj)
            }
        } catch (ex: Exception) {
            log.error("Failed to serialize: $ex")
            throw ex
        }
    }
}

