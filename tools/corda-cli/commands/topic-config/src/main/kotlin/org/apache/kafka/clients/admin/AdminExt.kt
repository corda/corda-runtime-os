package org.apache.kafka.clients.admin

import java.util.concurrent.TimeUnit

fun Admin.existingTopicNamesWithPrefix(prefix: String, wait: Long) =
    listTopics().names().get(wait, TimeUnit.SECONDS)
        .filter { it.startsWith(prefix) }
