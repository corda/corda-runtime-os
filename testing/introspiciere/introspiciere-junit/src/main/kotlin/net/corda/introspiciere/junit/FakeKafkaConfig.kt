package net.corda.introspiciere.junit

import net.corda.introspiciere.core.KafkaConfig

class FakeKafkaConfig(override val brokers: String) : KafkaConfig