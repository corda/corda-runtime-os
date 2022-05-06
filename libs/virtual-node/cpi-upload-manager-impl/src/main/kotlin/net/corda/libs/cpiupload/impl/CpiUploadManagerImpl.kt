package net.corda.libs.cpiupload.impl

import net.corda.chunking.ChunkWriter.Request
import net.corda.chunking.ChunkWriterFactory
import net.corda.chunking.RequestId
import net.corda.data.chunking.UploadStatus
import net.corda.data.chunking.UploadStatusKey
import net.corda.libs.cpiupload.CpiUploadManager
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import java.io.InputStream

/**
 * Uploads a CPI on the supplied [uploadTopic]
 *
 * Assumes that both [publisher] has been started and the subscription that consumes the
 * [UploadStatusProcessor] is also started.
 *
 * @param uploadTopic we write (publish) chunks to this topic
 * @param publisher the publisher used to write messages
 * @param subscription we take ownership of the subscription so that we can close it
 * @param statusProcessor used by the subscription to consume messages from the [uploadTopic]
 */
class CpiUploadManagerImpl(
    private val uploadTopic: String,
    private val publisher: Publisher,
    private val subscription: Subscription<UploadStatusKey, UploadStatus>,
    private val statusProcessor: UploadStatusProcessor
) : CpiUploadManager {

    override fun uploadCpi(cpiFileName: String, cpiContent: InputStream, forceUpload: Boolean): Request {
        val chunkWriter = ChunkWriterFactory.create(ChunkWriterFactory.SUGGESTED_CHUNK_SIZE, forceUpload).apply {
            onChunk {
                val futures = publisher.publish(listOf(Record(uploadTopic, it.requestId, it)))
                futures.forEach { f -> f.get() }
            }
        }
        return chunkWriter.write(cpiFileName, cpiContent)
    }

    override fun status(requestId: RequestId) : UploadStatus? = statusProcessor.status(requestId)

    override fun close() {
        publisher.close()
        subscription.close()
    }
}
