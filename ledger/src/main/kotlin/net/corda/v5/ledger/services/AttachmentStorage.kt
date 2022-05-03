package net.corda.v5.ledger.services

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.contracts.Attachment
import java.io.IOException
import java.io.InputStream
import java.nio.file.FileAlreadyExistsException

typealias AttachmentId = SecureHash

/**
 * An attachment store records potentially large binary objects, identified by their hash.
 */
@DoNotImplement
interface AttachmentStorage {

    /**
     * Returns a handle to a locally stored attachment, or null if it's not known. The handle can be used to open a stream for the data,
     * which will be a zip/jar file.
     *
     * @param id The [AttachmentId] of the attachment.
     *
     * @return The [Attachment] that matches the input [AttachmentId], or `null` if it does not exist.
     */
    fun openAttachment(id: AttachmentId): Attachment?

    /**
     * Inserts the given attachment with additional metadata.
     *
     * @param jar The input stream of the attachment/jar.
     * @param uploader Uploader name.
     * @param filename Name of the file.
     *
     * @throws IOException If there is an error reading from the [InputStream].
     */
    @Throws(FileAlreadyExistsException::class, IOException::class)
    fun importAttachment(jar: InputStream, uploader: String, filename: String?): AttachmentId

    /**
     * Checks if an attachment already exists.
     *
     * @param attachmentId The [AttachmentId] representing the attachment.
     *
     * @return true if it exists, false otherwise.
     */
    fun hasAttachment(attachmentId: AttachmentId): Boolean
}

/**
 * Thrown to indicate that an attachment was already uploaded to a Corda node.
 */
class DuplicateAttachmentException(attachmentHash: String) : java.nio.file.FileAlreadyExistsException(attachmentHash)