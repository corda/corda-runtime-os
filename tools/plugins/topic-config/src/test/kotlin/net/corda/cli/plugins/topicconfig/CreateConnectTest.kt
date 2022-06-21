package net.corda.cli.plugins.topicconfig

import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.acl.AccessControlEntry
import org.apache.kafka.common.acl.AclBinding
import org.apache.kafka.common.acl.AclOperation
import org.apache.kafka.common.acl.AclPermissionType
import org.apache.kafka.common.resource.PatternType
import org.apache.kafka.common.resource.ResourcePattern
import org.apache.kafka.common.resource.ResourceType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
<<<<<<< HEAD
=======
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.net.URL
import java.util.jar.JarEntry
import java.util.jar.JarFile
>>>>>>> CORE-5191 Connect directly to broker for topic creation

class CreateConnectTest {

    @Test
    fun `validate empty topic list`() {
        assertThat(command().getTopics(emptyList())).isEmpty()
    }

    @Test
    fun `validate new topic with no config`() {
        assertThat(command().getTopics(listOf(Create.TopicConfig("topic", emptyList(), emptyList(), emptyMap()))))
            .containsExactly(NewTopic("topic", 1, 1).configs(emptyMap()))
    }

    @Test
    fun `validate new topic with config`() {
        assertThat(command().getTopics(listOf(Create.TopicConfig("topic", emptyList(), emptyList(), mapOf("key" to "value")))))
            .containsExactly(NewTopic("topic", 1, 1).configs(mapOf("key" to "value")))
    }

    @Test
    fun `validate acls with consumer and producer`() {
        assertThat(command().getAclBindings(listOf(Create.TopicConfig("topic", listOf("consumer"), listOf("producer")))))
            .containsExactly(
<<<<<<< HEAD
                AclBinding(ResourcePattern(ResourceType.TOPIC, "topic", PatternType.LITERAL),
                    AccessControlEntry("User:consumer", "*", AclOperation.READ, AclPermissionType.ALLOW)),
                AclBinding(ResourcePattern(ResourceType.TOPIC, "topic", PatternType.LITERAL),
                    AccessControlEntry("User:producer", "*", AclOperation.WRITE, AclPermissionType.ALLOW))
=======
                AclBinding(ResourcePattern(ResourceType.TOPIC, "topic", PatternType.LITERAL), AccessControlEntry("User:consumer", "*", AclOperation.READ, AclPermissionType.ALLOW)),
                AclBinding(ResourcePattern(ResourceType.TOPIC, "topic", PatternType.LITERAL), AccessControlEntry("User:producer", "*", AclOperation.WRITE, AclPermissionType.ALLOW))
>>>>>>> CORE-5191 Connect directly to broker for topic creation
            )
    }

    private fun command() : CreateConnect {
        val createConnect = CreateConnect()
        createConnect.create = Create()
        createConnect.create!!.topic = TopicPlugin.Topic()
        return createConnect
    }
}
