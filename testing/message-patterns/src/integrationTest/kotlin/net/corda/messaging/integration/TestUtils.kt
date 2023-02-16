package net.corda.messaging.integration

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.util.*
import net.corda.data.demo.DemoRecord
import net.corda.data.flow.event.Wakeup
import net.corda.messaging.api.records.Record
import net.corda.messaging.integration.IntegrationTestProperties.Companion.CLIENT_ID
import org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG
import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.osgi.framework.BundleContext
import org.osgi.framework.FrameworkUtil


fun BundleContext.isDBBundle() = bundles.find { it.symbolicName.contains("db-message-bus-impl") } != null

fun getDemoRecords(topic: String, recordCount: Int, keyCount: Int): List<Record<*, *>> {
    val records = mutableListOf<Record<*, *>>()
    for (i in 1..keyCount) {
        val key = "key$i"
        for (j in 1..recordCount) {
            records.add(Record(topic, key, DemoRecord(j)))
        }
    }
    return records
}

fun getDummyRecords(topic: String, recordCount: Int, keyCount: Int): List<Record<*, *>> {
    val records = mutableListOf<Record<*, *>>()
    for (i in 1..keyCount) {
        val key = "key$i"
        for (j in 1..recordCount) {
            records.add(Record(topic, key, Wakeup()))
        }
    }
    return records
}

fun getStringRecords(topic: String, recordCount: Int, keyCount: Int): List<Record<String, String>> {
    val records = mutableListOf<Record<String, String>>()
    for (i in 1..keyCount) {
        val key = "key$i"
        for (j in 1..recordCount) {
            records.add(Record(topic, key, j.toString()))
        }
    }
    return records
}

fun getKafkaProperties(): Properties {
    val kafkaProperties = Properties()
    kafkaProperties[BOOTSTRAP_SERVERS_CONFIG] = IntegrationTestProperties.BOOTSTRAP_SERVERS_VALUE
    kafkaProperties[CLIENT_ID] = "test"
    return kafkaProperties
}

fun getTopicConfig(template: String): Config {
    return ConfigFactory.parseString(template)
}

class KafkaOnlyTest: ExecutionCondition {
    override fun evaluateExecutionCondition(context: ExtensionContext?): ConditionEvaluationResult {
        val bundleContext = FrameworkUtil.getBundle(this::class.java).bundleContext
        return if (bundleContext.isDBBundle()) {
            ConditionEvaluationResult.disabled("Kafka Only tests don't run on DB")
        } else {
            ConditionEvaluationResult.enabled("Kafka test can run")
        }
    }
}

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(KafkaOnlyTest::class)
annotation class KafkaOnly
