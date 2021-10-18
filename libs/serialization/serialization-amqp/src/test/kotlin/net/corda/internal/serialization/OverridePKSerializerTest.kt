package net.corda.internal.serialization

import net.corda.v5.serialization.SerializationContext
import net.corda.v5.serialization.SerializationCustomSerializer
import org.mockito.kotlin.mock
import java.security.PublicKey

class OverridePKSerializerTest {
    class SerializerTestException(message: String) : Exception(message)

    class TestPublicKeySerializer : SerializationCustomSerializer<PublicKey, ByteArray> {
        override fun toProxy(obj: PublicKey): ByteArray = throw SerializerTestException("Custom write call")
        override fun fromProxy(proxy: ByteArray): PublicKey = throw SerializerTestException("Custom read call")
    }

    class AMQPTestSerializationScheme : AbstractAMQPSerializationScheme(mock()) {

        override fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase) = true

        override val publicKeySerializer = TestPublicKeySerializer()
    }

    class TestPublicKey : PublicKey {
        override fun getAlgorithm(): String {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getEncoded(): ByteArray {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getFormat(): String {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }
    }
}