package net.corda.messaging.kafka.integration

class TopicTemplates {
    companion object {
        const val DLQ_SUFFIX  = ".DLQ"
        const val COMPACTED_TOPIC1  = "CompactedTopic1"
        const val COMPACTED_TOPIC1_TEMPLATE = """topics = [ 
                    { 
                        topicName = "$COMPACTED_TOPIC1" 
                        numPartitions = 1 
                        replicationFactor = 3 
                        config { 
                            cleanup.policy=compact 
                        } 
                    } 
                ]"""

        const val DURABLE_TOPIC1  = "DurableTopic1"
        const val DURABLE_TOPIC1_TEMPLATE = """topics = [ 
                    { 
                        topicName = "$DURABLE_TOPIC1" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    } 
                ]"""

        const val EVENT_TOPIC1  = "EventTopic1"
        const val EVENT_TOPIC1_TEMPLATE = """topics = [ 
                    { 
                        topicName = "$EVENT_TOPIC1" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    }, 
                    { 
                        topicName = "$EVENT_TOPIC1$DLQ_SUFFIX" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    }, 
                    { 
                        topicName = "$EVENT_TOPIC1.state" 
                        numPartitions = 2 
                        replicationFactor = 3 
                        config { 
                            cleanup.policy=compact 
                        } 
                    } 
                ]"""

        const val EVENT_TOPIC2  = "EventTopic2"
        const val EVENT_TOPIC2_TEMPLATE = """topics = [ 
                    { 
                        topicName = "$EVENT_TOPIC2" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    }, 
                    { 
                        topicName = "$EVENT_TOPIC2$DLQ_SUFFIX" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    }, 
                    { 
                        topicName = "$EVENT_TOPIC2.state" 
                        numPartitions = 2 
                        replicationFactor = 3 
                        config { 
                            cleanup.policy=compact 
                        } 
                    } 
                ]"""

        const val EVENT_TOPIC3  = "EventTopic3"
        const val EVENT_TOPIC3_TEMPLATE = """topics = [ 
                    { 
                        topicName = "$EVENT_TOPIC3" 
                        numPartitions = 1 
                        replicationFactor = 3 
                    },
                    { 
                        topicName = "$EVENT_TOPIC3$DLQ_SUFFIX" 
                        numPartitions = 1 
                        replicationFactor = 3 
                    }, 
                    { 
                        topicName = "$EVENT_TOPIC3.state" 
                        numPartitions = 1 
                        replicationFactor = 3 
                        config { 
                            cleanup.policy=compact 
                        } 
                    } 
                ]"""

        const val EVENT_TOPIC4  = "EventTopic4"
        const val EVENT_TOPIC4_TEMPLATE = """topics = [ 
                    { 
                        topicName = "$EVENT_TOPIC4" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    },
                    { 
                        topicName = "$EVENT_TOPIC4$DLQ_SUFFIX" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    },
                    { 
                        topicName = "$EVENT_TOPIC4.state" 
                        numPartitions = 2 
                        replicationFactor = 3 
                        config { 
                            cleanup.policy=compact 
                        } 
                    } 
                ]"""

        const val EVENT_TOPIC5  = "EventTopic5"
        const val EVENT_TOPIC5_TEMPLATE = """topics = [ 
                    { 
                        topicName = "$EVENT_TOPIC5" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    }, 
                    { 
                        topicName = "$EVENT_TOPIC5$DLQ_SUFFIX" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    }, 
                    { 
                        topicName = "$EVENT_TOPIC5.state" 
                        numPartitions = 2 
                        replicationFactor = 3 
                        config { 
                            cleanup.policy=compact 
                        } 
                    } 
                ]"""

        const val EVENT_TOPIC6  = "EventTopic6"
        const val EVENT_TOPIC6_TEMPLATE = """topics = [ 
                    { 
                        topicName = "$EVENT_TOPIC6" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    },
                    { 
                        topicName = "$EVENT_TOPIC6$DLQ_SUFFIX" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    },
                    { 
                        topicName = "$EVENT_TOPIC6.state" 
                        numPartitions = 2 
                        replicationFactor = 3 
                        config { 
                            cleanup.policy=compact 
                        } 
                    } 
                ]"""
    }
}
