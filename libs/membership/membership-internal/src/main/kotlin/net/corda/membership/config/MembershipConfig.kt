package net.corda.membership.config

import net.corda.v5.base.util.uncheckedCast

interface MembershipConfig {

    operator fun get(key: String): Any?

    val keys: Set<String>
}

interface MembershipKafkaConfig : MembershipConfig
interface MembershipKafkaPersistenceConfig : MembershipKafkaConfig
interface MembershipKafkaMemberListConfig : MembershipKafkaConfig

inline fun <reified T> MembershipConfig.getValue(key: String): T = this[key] as T

val MembershipConfig.kafkaConfig : MembershipKafkaConfig?
    get() = uncheckedCast(get(MembershipConfigConstants.Kafka.CONFIG_KEY))

val MembershipKafkaConfig.groupName : String?
    get() = uncheckedCast(get(MembershipConfigConstants.Kafka.GROUP_NAME_KEY))

val MembershipKafkaConfig.topicName : String?
    get() = uncheckedCast(get(MembershipConfigConstants.Kafka.TOPIC_NAME_KEY))

val MembershipKafkaConfig.persistence: MembershipKafkaPersistenceConfig?
    get() = uncheckedCast(get(MembershipConfigConstants.Kafka.Persistence.CONFIG_KEY))

val MembershipKafkaPersistenceConfig.memberList: MembershipKafkaMemberListConfig?
    get() = uncheckedCast(get(MembershipConfigConstants.Kafka.Persistence.MemberList.CONFIG_KEY))
