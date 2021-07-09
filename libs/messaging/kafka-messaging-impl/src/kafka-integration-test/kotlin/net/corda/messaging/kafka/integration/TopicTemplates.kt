package net.corda.messaging.kafka.integration

class TopicTemplates {
    companion object {
        const val COMPACTED_TOPIC1  = "CompactedTopic1"
        const val COMPACTED_TOPIC1_TEMPLATE = "topics = [" +
                "    {\n" +
                "        topicName = \"$COMPACTED_TOPIC1\"\n" +
                "        numPartitions = 1\n" +
                "        replicationFactor = 3\n" +
                "        config {\n" +
                "            cleanup.policy=compact\n" +
                "        }\n" +
                "    }\n" +
                "]"

        const val EVENT_TOPIC1  = "EventTopic1"
        const val EVENT_TOPIC1_TEMPLATE = "topics = [" +
                "    {\n" +
                "        topicName = \"$EVENT_TOPIC1\"\n" +
                "        numPartitions = 2\n" +
                "        replicationFactor = 3\n" +
                "    }\n" +
                "]"

        const val EVENTSTATE_TOPIC1 = "EventTopic1.state"
        const val EVENTSTATE_TOPIC1_TEMPLATE = "topics = [" +
                "    {\n" +
                "        topicName = \"$EVENTSTATE_TOPIC1\"\n" +
                "        numPartitions = 2\n" +
                "        replicationFactor = 3\n" +
                "        config {\n" +
                "            cleanup.policy=compact\n" +
                "        }\n" +
                "    }\n" +
                "]"

        const val EVENT_TOPIC2  = "EventTopic2"
        const val EVENT_TOPIC2_TEMPLATE = "topics = [" +
                "    {\n" +
                "        topicName = \"$EVENT_TOPIC2\"\n" +
                "        numPartitions = 2\n" +
                "        replicationFactor = 3\n" +
                "    }\n" +
                "]"

        const val EVENTSTATE_TOPIC2 = "EventTopic2.state"
        const val EVENTSTATE_TOPIC2_TEMPLATE = "topics = [" +
                "    {\n" +
                "        topicName = \"$EVENTSTATE_TOPIC2\"\n" +
                "        numPartitions = 2\n" +
                "        replicationFactor = 3\n" +
                "        config {\n" +
                "            cleanup.policy=compact\n" +
                "        }\n" +
                "    }\n" +
                "]"

        const val EVENTSTATE_OUTPUT2 = "EventStateOutputTopic2"
    }
}
