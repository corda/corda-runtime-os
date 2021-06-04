package net.corda.comp.kafka.topic.admin

import com.typesafe.config.Config

interface TopicAdmin {

    fun createTopic(props: String, topicTemplate: String): Config

}