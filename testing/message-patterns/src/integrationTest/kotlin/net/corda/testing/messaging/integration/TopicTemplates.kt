package net.corda.testing.messaging.integration

import net.corda.schema.Schemas.getDLQTopic
import net.corda.schema.Schemas.getStateAndEventStateTopic
import net.corda.testing.messaging.integration.IntegrationTestProperties.Companion.getBundleContext
import java.util.UUID

class TopicTemplates {
    companion object {
        val TEST_TOPIC_PREFIX_VALUE = "testPrefix-${UUID.randomUUID()}-"
        private val TEST_TOPIC_PREFIX = if (getBundleContext().isDBBundle()) "" else TEST_TOPIC_PREFIX_VALUE
        const val COMPACTED_TOPIC1 = "CompactedTopic1"
        val COMPACTED_TOPIC1_TEMPLATE = """topics = [ 
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$COMPACTED_TOPIC1" 
                        numPartitions = 1 
                        replicationFactor = 3 
                        config { 
                            cleanup.policy=compact 
                        } 
                    } 
                ]"""

        const val COMPACTED_TOPIC2 = "CompactedTopic2"
        val COMPACTED_TOPIC2_TEMPLATE = """topics = [ 
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$COMPACTED_TOPIC2" 
                        numPartitions = 1 
                        replicationFactor = 3 
                        config { 
                            cleanup.policy=compact 
                        } 
                    } 
                ]"""

        const val DURABLE_TOPIC1 = "DurableTopic1"
        val DURABLE_TOPIC1_TEMPLATE = """topics = [ 
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$DURABLE_TOPIC1" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    } 
                ]"""
        const val DURABLE_TOPIC2 = "DurableTopic2"
        val DURABLE_TOPIC2_TEMPLATE = """topics = [ 
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$DURABLE_TOPIC2" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    } 
                ]"""
        const val DURABLE_TOPIC3 = "DurableTopic3"
        const val DURABLE_TOPIC3_OUTPUT = "$DURABLE_TOPIC3-output"
        val DURABLE_TOPIC3_DLQ = getDLQTopic(DURABLE_TOPIC3)
        val DURABLE_TOPIC3_TEMPLATE = """topics = [ 
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$DURABLE_TOPIC3" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    },
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$DURABLE_TOPIC3_DLQ" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    },
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$DURABLE_TOPIC3_OUTPUT" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    }
                ]"""

        const val EVENT_LOG_TOPIC1 = "EventLogTopic1"
        val EVENT_LOG_TOPIC1_TEMPLATE = """topics = [ 
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$EVENT_LOG_TOPIC1" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    } 
                ]"""
        const val EVENT_LOG_TOPIC2 = "EventLogTopic2"
        const val EVENT_LOG_TOPIC2_OUTPUT = "$EVENT_LOG_TOPIC2-output"
        val EVENT_LOG_TOPIC2_TEMPLATE = """topics = [ 
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$EVENT_LOG_TOPIC2" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    },
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$EVENT_LOG_TOPIC2_OUTPUT" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    }
                ]"""

        private const val PUBSUB_TOPIC1 = "PubSubTopic1"
        val PUBSUB_TOPIC1_TEMPLATE = """topics = [ 
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$PUBSUB_TOPIC1" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    } 
                ]"""

        const val PUBLISHER_TEST_DURABLE_TOPIC1 = "PublisherTestDurableTopic1"
        val PUBLISHER_TEST_DURABLE_TOPIC1_TEMPLATE = """topics = [ 
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$PUBLISHER_TEST_DURABLE_TOPIC1" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    } 
                ]"""

        const val PUBLISHER_TEST_DURABLE_TOPIC2 = "PublisherTestDurableTopic2"
        val PUBLISHER_TEST_DURABLE_TOPIC2_TEMPLATE = """topics = [ 
            { 
                topicName = "$TEST_TOPIC_PREFIX$PUBLISHER_TEST_DURABLE_TOPIC2" 
                numPartitions = 2 
                replicationFactor = 3 
            } 
        ]"""

        const val PUBLISHER_TEST_DURABLE_TOPIC3 = "PublisherTestDurableTopic3"
        val PUBLISHER_TEST_DURABLE_TOPIC3_TEMPLATE = """topics = [ 
            { 
                topicName = "$TEST_TOPIC_PREFIX$PUBLISHER_TEST_DURABLE_TOPIC3" 
                numPartitions = 2 
                replicationFactor = 3 
            } 
        ]"""
        const val PUBLISHER_TEST_DURABLE_TOPIC4 = "PublisherTestDurableTopic3"
        val PUBLISHER_TEST_DURABLE_TOPIC4_TEMPLATE = """topics = [ 
            { 
                topicName = "$TEST_TOPIC_PREFIX$PUBLISHER_TEST_DURABLE_TOPIC4" 
                numPartitions = 2 
                replicationFactor = 3 
            } 
        ]"""

        const val EVENT_TOPIC1 = "EventTopic1"
        private val EVENT_TOPIC1_DLQ = getDLQTopic(EVENT_TOPIC1)
        private val EVENT_TOPIC1_STATE = getStateAndEventStateTopic(EVENT_TOPIC1)
        val EVENT_TOPIC1_TEMPLATE = """topics = [ 
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$EVENT_TOPIC1" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    }, 
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$EVENT_TOPIC1_DLQ" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    }, 
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$EVENT_TOPIC1_STATE" 
                        numPartitions = 2 
                        replicationFactor = 3 
                        config { 
                            cleanup.policy=compact 
                        } 
                    } 
                ]"""

        const val EVENT_TOPIC2 = "EventTopic2"
        const val EVENTSTATE_OUTPUT2 = "EventStateOutputTopic2"
        private val EVENT_TOPIC2_DLQ = getDLQTopic(EVENT_TOPIC2)
        private val EVENT_TOPIC2_STATE = getStateAndEventStateTopic(EVENT_TOPIC2)
        val EVENT_TOPIC2_TEMPLATE = """topics = [ 
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$EVENT_TOPIC2" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    }, 
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$EVENT_TOPIC2_DLQ" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    }, 
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$EVENT_TOPIC2_STATE" 
                        numPartitions = 2 
                        replicationFactor = 3 
                        config { 
                            cleanup.policy=compact 
                        } 
                    },
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$EVENTSTATE_OUTPUT2" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    }
                ]"""

        const val EVENT_TOPIC3 = "EventTopic3"
        const val EVENTSTATE_OUTPUT3 = "EventStateOutputTopic3"
        private val EVENT_TOPIC3_DLQ = getDLQTopic(EVENT_TOPIC3)
        private val EVENT_TOPIC3_STATE = getStateAndEventStateTopic(EVENT_TOPIC3)
        val EVENT_TOPIC3_TEMPLATE = """topics = [ 
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$EVENT_TOPIC3" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    }, 
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$EVENT_TOPIC3_DLQ" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    }, 
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$EVENT_TOPIC3_STATE" 
                        numPartitions = 2 
                        replicationFactor = 3 
                        config { 
                            cleanup.policy=compact 
                        } 
                    },
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$EVENTSTATE_OUTPUT3" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    }
                ]"""

        const val EVENT_TOPIC4 = "EventTopic4"
        const val EVENTSTATE_OUTPUT4 = "EventStateOutputTopic4"
        private val EVENT_TOPIC4_DLQ = getDLQTopic(EVENT_TOPIC4)
        private val EVENT_TOPIC4_STATE = getStateAndEventStateTopic(EVENT_TOPIC4)
        val EVENT_TOPIC4_TEMPLATE = """topics = [ 
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$EVENT_TOPIC4" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    }, 
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$EVENT_TOPIC4_DLQ" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    }, 
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$EVENT_TOPIC4_STATE" 
                        numPartitions = 2 
                        replicationFactor = 3 
                        config { 
                            cleanup.policy=compact 
                        } 
                    },
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$EVENTSTATE_OUTPUT4" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    }
                ]"""

        const val EVENT_TOPIC5 = "EventTopic5"
        const val EVENTSTATE_OUTPUT5 = "EventStateOutputTopic5"
        val EVENT_TOPIC5_DLQ = getDLQTopic(EVENT_TOPIC5)
        private val EVENT_TOPIC5_STATE = getStateAndEventStateTopic(EVENT_TOPIC5)
        val EVENT_TOPIC5_TEMPLATE = """topics = [ 
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$EVENT_TOPIC5" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    }, 
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$EVENT_TOPIC5_DLQ" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    }, 
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$EVENT_TOPIC5_STATE" 
                        numPartitions = 2 
                        replicationFactor = 3 
                        config { 
                            cleanup.policy=compact 
                        } 
                    },
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$EVENTSTATE_OUTPUT5" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    }
                ]"""

        const val EVENT_TOPIC6 = "EventTopic6"
        const val EVENTSTATE_OUTPUT6 = "EventStateOutputTopic6"
        private val EVENT_TOPIC6_DLQ = getDLQTopic(EVENT_TOPIC6)
        private val EVENT_TOPIC6_STATE = getStateAndEventStateTopic(EVENT_TOPIC6)
        val EVENT_TOPIC6_TEMPLATE = """topics = [ 
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$EVENT_TOPIC6" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    }, 
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$EVENT_TOPIC6_DLQ" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    }, 
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$EVENT_TOPIC6_STATE" 
                        numPartitions = 2 
                        replicationFactor = 3 
                        config { 
                            cleanup.policy=compact 
                        } 
                    },
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$EVENTSTATE_OUTPUT6" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    }
                ]"""

        const val EVENT_TOPIC7 = "EventTopic7"
        const val EVENTSTATE_OUTPUT7 = "EventStateOutputTopic7"
        val EVENT_TOPIC7_DLQ = getDLQTopic(EVENT_TOPIC7)
        private val EVENT_TOPIC7_STATE = getStateAndEventStateTopic(EVENT_TOPIC7)
        val EVENT_TOPIC7_TEMPLATE = """topics = [ 
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$EVENT_TOPIC7" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    }, 
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$EVENT_TOPIC7_DLQ" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    }, 
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$EVENT_TOPIC7_STATE" 
                        numPartitions = 2 
                        replicationFactor = 3 
                        config { 
                            cleanup.policy=compact 
                        } 
                    },
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$EVENTSTATE_OUTPUT7" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    }
                ]"""


        const val RPC_TOPIC1 = "RPCTopic1"
        val RPC_TOPIC1_TEMPLATE = """topics = [
                    {
                        topicName = "$TEST_TOPIC_PREFIX$RPC_TOPIC1" 
                        numPartitions = 1
                        replicationFactor = 3
                        config {
                            cleanup.policy=compact
                        }
                    }
                ]"""

        private const val RPC_RESPONSE_TOPIC1 = "RPCTopic1.resp"
        val RPC_RESPONSE_TOPIC1_TEMPLATE = """topics = [
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$RPC_RESPONSE_TOPIC1"
                        numPartitions = 1
                        replicationFactor = 3
                        config { 
                            cleanup.policy=compact
                        } 
                    } 
                ]"""

        const val RPC_TOPIC2 = "RPCTopic2"
        val RPC_TOPIC2_TEMPLATE = """topics = [
                    {
                        topicName = "$TEST_TOPIC_PREFIX$RPC_TOPIC2" 
                        numPartitions = 1
                        replicationFactor = 3
                        config {
                            cleanup.policy=compact
                        }
                    }
                ]"""

        private const val RPC_RESPONSE_TOPIC2 = "RPCTopic2.resp"
        val RPC_RESPONSE_TOPIC2_TEMPLATE = """topics = [
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$RPC_RESPONSE_TOPIC2"
                        numPartitions = 1
                        replicationFactor = 3
                        config { 
                            cleanup.policy=compact
                        } 
                    } 
                ]"""

        const val RPC_TOPIC3 = "RPCTopic3"
        val RPC_TOPIC3_TEMPLATE = """topics = [
                    {
                        topicName = "$TEST_TOPIC_PREFIX$RPC_TOPIC3" 
                        numPartitions = 1
                        replicationFactor = 3
                        config {
                            cleanup.policy=compact
                        }
                    }
                ]"""

        private const val RPC_RESPONSE_TOPIC3 = "RPCTopic3.resp"
        val RPC_RESPONSE_TOPIC3_TEMPLATE = """topics = [
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$RPC_RESPONSE_TOPIC3"
                        numPartitions = 1
                        replicationFactor = 3
                        config { 
                            cleanup.policy=compact
                        } 
                    } 
                ]"""

        const val RPC_TOPIC4 = "RPCTopic4"
        val RPC_TOPIC4_TEMPLATE = """topics = [
                    {
                        topicName = "$TEST_TOPIC_PREFIX$RPC_TOPIC4" 
                        numPartitions = 1
                        replicationFactor = 3
                        config {
                            cleanup.policy=compact
                        }
                    }
                ]"""

        private const val RPC_RESPONSE_TOPIC4 = "RPCTopic4.resp"
        val RPC_RESPONSE_TOPIC4_TEMPLATE = """topics = [
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$RPC_RESPONSE_TOPIC4"
                        numPartitions = 1
                        replicationFactor = 3
                        config { 
                            cleanup.policy=compact
                        } 
                    } 
                ]"""

        const val RPC_TOPIC5 = "RPCTopic5"
        val RPC_TOPIC5_TEMPLATE = """topics = [
                    {
                        topicName = "$TEST_TOPIC_PREFIX$RPC_TOPIC5" 
                        numPartitions = 1
                        replicationFactor = 3
                        config {
                            cleanup.policy=compact
                        }
                    }
                ]"""

        private const val RPC_RESPONSE_TOPIC5 = "RPCTopic5.resp"
        val RPC_RESPONSE_TOPIC5_TEMPLATE = """topics = [
                    { 
                        topicName = "$TEST_TOPIC_PREFIX$RPC_RESPONSE_TOPIC5"
                        numPartitions = 1
                        replicationFactor = 3
                        config { 
                            cleanup.policy=compact
                        } 
                    } 
                ]"""

        const val MEDIATOR_TOPIC1 = "MediatorTopic1"
        val MEDIATOR_TOPIC1_TEMPLATE = """topics = [ 
            { 
                topicName = "$TEST_TOPIC_PREFIX$MEDIATOR_TOPIC1" 
                numPartitions = 2 
                replicationFactor = 3 
            } 
        ]"""

        const val MEDIATOR_TOPIC1_OUTPUT = "MediatorTopic1Output"
        val MEDIATOR_TOPIC1_OUTPUT_TEMPLATE = """topics = [ 
            { 
                topicName = "$TEST_TOPIC_PREFIX$MEDIATOR_TOPIC1_OUTPUT" 
                numPartitions = 2 
                replicationFactor = 3 
            } 
        ]"""
        const val MEDIATOR_TOPIC2 = "MediatorTopic2"
        val MEDIATOR_TOPIC2_TEMPLATE = """topics = [ 
            { 
                topicName = "$TEST_TOPIC_PREFIX$MEDIATOR_TOPIC2" 
                numPartitions = 2 
                replicationFactor = 3 
            } 
        ]"""


        const val MEDIATOR_TOPIC2_OUTPUT = "${MEDIATOR_TOPIC2}Output"
        val MEDIATOR_TOPIC2_OUTPUT_TEMPLATE = """topics = [ 
            { 
                topicName = "$TEST_TOPIC_PREFIX$MEDIATOR_TOPIC2_OUTPUT" 
                numPartitions = 2 
                replicationFactor = 3 
            } 
        ]"""

        const val MEDIATOR_TOPIC3 = "MediatorTopic3"
        val MEDIATOR_TOPIC3_TEMPLATE = """topics = [ 
            { 
                topicName = "$TEST_TOPIC_PREFIX$MEDIATOR_TOPIC3" 
                numPartitions = 2 
                replicationFactor = 3 
            } 
        ]"""


        const val MEDIATOR_TOPIC3_OUTPUT = "${MEDIATOR_TOPIC3}Output"
        val MEDIATOR_TOPIC3_OUTPUT_TEMPLATE = """topics = [ 
            { 
                topicName = "$TEST_TOPIC_PREFIX$MEDIATOR_TOPIC3_OUTPUT" 
                numPartitions = 2 
                replicationFactor = 3 
            } 
        ]"""

        const val MEDIATOR_TOPIC4 = "MediatorTopic4"
        val MEDIATOR_TOPIC4_TEMPLATE = """topics = [ 
            { 
                topicName = "$TEST_TOPIC_PREFIX$MEDIATOR_TOPIC4" 
                numPartitions = 2 
                replicationFactor = 3 
            } 
        ]"""


        const val MEDIATOR_TOPIC4_OUTPUT = "${MEDIATOR_TOPIC4}Output"
        val MEDIATOR_TOPIC4_OUTPUT_TEMPLATE = """topics = [ 
            { 
                topicName = "$TEST_TOPIC_PREFIX$MEDIATOR_TOPIC4_OUTPUT" 
                numPartitions = 2 
                replicationFactor = 3 
            } 
        ]"""

        const val MEDIATOR_TOPIC5 = "MediatorTopic5"
        val MEDIATOR_TOPIC5_TEMPLATE = """topics = [ 
            { 
                topicName = "$TEST_TOPIC_PREFIX$MEDIATOR_TOPIC5" 
                numPartitions = 2 
                replicationFactor = 3 
            } 
        ]"""

        const val MEDIATOR_TOPIC5_OUTPUT = "${MEDIATOR_TOPIC5}Output"
        val MEDIATOR_TOPIC5_OUTPUT_TEMPLATE = """topics = [ 
            { 
                topicName = "$TEST_TOPIC_PREFIX$MEDIATOR_TOPIC5_OUTPUT" 
                numPartitions = 2 
                replicationFactor = 3 
            } 
        ]"""
    }
}