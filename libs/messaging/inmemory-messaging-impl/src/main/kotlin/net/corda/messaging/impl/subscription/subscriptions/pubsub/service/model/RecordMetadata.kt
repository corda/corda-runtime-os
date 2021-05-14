package net.corda.messaging.impl.subscription.subscriptions.pubsub.service.model

import net.corda.messaging.api.records.Record

class RecordMetadata(var offset: Int, var record: Record<*, *>)
