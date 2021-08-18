package net.corda.internal.serialization

import net.corda.v5.serialization.SerializationContext
import net.corda.internal.serialization.amqp.CustomSerializer
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.Metadata
import net.corda.internal.serialization.amqp.Schema
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializationSchemas
import net.corda.internal.serialization.amqp.SerializerFactory
import org.apache.qpid.proton.codec.Data
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.lang.reflect.Type
import java.security.PublicKey

class OverridePKSerializerTest {
    class SerializerTestException(message: String) : Exception(message)

    class TestPublicKeySerializer : CustomSerializer.Implements<PublicKey>(PublicKey::class.java) {
        override fun writeDescribedObject(obj: PublicKey, data: Data, type: Type, output: SerializationOutput,
                                          context: SerializationContext
        ) {
            throw SerializerTestException("Custom write call")
        }

        override fun readObject(obj: Any, serializationSchemas: SerializationSchemas, metadata: Metadata,
                                input: DeserializationInput, context: SerializationContext
        ): PublicKey {
            throw SerializerTestException("Custom read call")
        }

        override val schemaForDocumentation: Schema
            get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
    }

    class AMQPTestSerializationScheme : AbstractAMQPSerializationScheme() {
        override fun rpcServerSerializerFactory(context: SerializationContext): SerializerFactory {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase) = true
        override fun rpcClientSerializerFactory(context: SerializationContext): SerializerFactory {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

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