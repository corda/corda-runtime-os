# Introspiciere-junit

This module is a programmatic API and JUnit extensions to interact with the introspiciere service.

## Dependencies to run the integration tests

1. Run a local Kafka with Strimzi following instrucctions under [introspiciere](../README.md)

## Start an in-memory introspoiciere server

```kotlin
class DeployInmemoryIntrospiciereServer {

    // Use one of both methods to start the server. 
    // When the extension is in the companion object the server starts at the beginning of the test suite and stops
    // at the end.
    
    companion object {
        @RegisterExtension
        @JvmStatic
        private val introspiciere = InMemoryIntrospiciereServer(
            // This only works locally at the moment. For CI it should read
            // this for an environment variable or from a config file
            kafkaBrokers = getMinikubeKafkaBroker()
        )
    }

    // When the extension is in the class itself, the server starts and stops on each test.
    
    @RegisterExtension
    private val introspiciere = InMemoryIntrospiciereServer(
        // This only works locally at the moment. For CI it should read
        // this for an environment variable or from a config file
        kafkaBrokers = getMinikubeKafkaBroker()
    )
    
    @Test
    fun `create a topic`() {
        introspiciere.client
            .createTopic("my-topic")
    }
    
    @Test
    fun `write a message to a topic`() {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(571)
        val pair = generator.generateKeyPair()
        return KeyPairEntry(
            KeyAlgorithm.ECDSA,
            ByteBuffer.wrap(pair.public.encoded),
            ByteBuffer.wrap(pair.private.encoded)
        )
        
        introspiciere.client
            .write("my-topic", "a-key", keyPairEntry)
    }
    
    @Test
    fun `read all messages from a topic`() {
        introspiciere.client
            .read<KeyPairEntry>("my-topic", "a-key").forEach(::println)
    }
}
```