package net.corda.cpi.integration.test

class TopicTemplates {
    companion object {
        const val RPC_TOPIC  = "RPCTopic"
        const val RPC_TOPIC_TEMPLATE = """topics = [
                    {
                        topicName = "$RPC_TOPIC" 
                        numPartitions = 1
                        replicationFactor = 3
                        config {
                            cleanup.policy=compact
                        }
                    }
                ]"""

        const val RPC_RESPONSE_TOPIC  = "RPCTopic.resp"
        const val RPC_RESPONSE_TOPIC_TEMPLATE = """topics = [
                    { 
                        topicName = "$RPC_RESPONSE_TOPIC"
                        numPartitions = 1
                        replicationFactor = 3
                        config { 
                            cleanup.policy=compact
                        } 
                    } 
                ]"""
    }
}