package net.corda.cpi.integration.test

import java.util.Properties

fun getKafkaProperties(): Properties {
    val kafkaProperties = Properties()
    kafkaProperties[IntegrationTestProperties.BOOTSTRAP_SERVERS] = IntegrationTestProperties.BOOTSTRAP_SERVERS_VALUE
    return kafkaProperties
}