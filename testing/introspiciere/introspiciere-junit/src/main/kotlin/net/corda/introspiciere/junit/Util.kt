package net.corda.introspiciere.junit

import java.nio.ByteBuffer
import java.util.concurrent.ThreadLocalRandom
import kotlin.streams.asSequence


private val charPool: List<Char> = ('a'..'z') + ('0'..'9')

/**
 * Concatenates 8 random alphanumerical values prefixed with a `-`.
 */
val String.random8: String
    get() = "$this-" + ThreadLocalRandom.current().ints(8L, 0, charPool.size)
        .asSequence().map(charPool::get).joinToString("")

/**
 * Returns the ip:port to connect to a Kafka that has been deployed following the instructions in the README.
 *
 * `ip = minikube ip`
 *
 * `port = kubectl get service my-cluster-kafka-bootstrap -n kafka`
 */
fun getMinikubeKafkaBroker(): String {
    val minikubeIp = ProcessBuilder("minikube", "ip").start()
    val kafkaPort =
        ProcessBuilder("kubectl", "get", "service", "my-cluster-kafka-external-bootstrap", "-n", "kafka").start()

    if (minikubeIp.waitFor() != 0) {
        throw Exception("Fail to get minikube ip.")
    }

    if (kafkaPort.waitFor() != 0) {
        throw Exception("Fail to get kafka port.")
    }

    val ip = minikubeIp.inputStream.bufferedReader().readText().trim()
    val port = kafkaPort.inputStream.bufferedReader().readText()
        // regex: 9094:32440/TCP
        .let(Regex("\\d+:(\\d+)/TCP")::find)!!.groupValues[1]

    return "$ip:$port"
}

fun ByteBuffer.toByteArray(): ByteArray {
    val array = ByteArray(remaining())
    get(array)
    return array
}
