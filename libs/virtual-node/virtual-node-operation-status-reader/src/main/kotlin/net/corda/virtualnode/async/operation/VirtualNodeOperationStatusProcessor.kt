package net.corda.virtualnode.async.operation

import net.corda.data.virtualnode.VirtualNodeOperationStatus
import net.corda.messaging.api.processor.CompactedProcessor

interface VirtualNodeOperationStatusProcessor: CompactedProcessor<String, VirtualNodeOperationStatus>