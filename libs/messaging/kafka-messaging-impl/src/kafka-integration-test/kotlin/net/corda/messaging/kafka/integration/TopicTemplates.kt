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
                "    },\n" +
                "    {\n" +
                "        topicName = \"$EVENT_TOPIC1.state\"\n" +
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
                "    },\n" +
                "    {\n" +
                "        topicName = \"$EVENT_TOPIC2.state\"\n" +
                "        numPartitions = 2\n" +
                "        replicationFactor = 3\n" +
                "        config {\n" +
                "            cleanup.policy=compact\n" +
                "        }\n" +
                "    }\n" +
                "]"

        const val EVENT_TOPIC3  = "EventTopic3"
        const val EVENT_TOPIC3_TEMPLATE = "topics = [" +
                "    {\n" +
                "        topicName = \"$EVENT_TOPIC3\"\n" +
                "        numPartitions = 2\n" +
                "        replicationFactor = 3\n" +
                "    },\n" +
                "    {\n" +
                "        topicName = \"$EVENT_TOPIC3.state\"\n" +
                "        numPartitions = 2\n" +
                "        replicationFactor = 3\n" +
                "        config {\n" +
                "            cleanup.policy=compact\n" +
                "        }\n" +
                "    }\n" +
                "]"
    }
}
