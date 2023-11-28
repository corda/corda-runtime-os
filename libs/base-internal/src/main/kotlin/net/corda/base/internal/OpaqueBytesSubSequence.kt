package net.corda.base.internal

/**
 * Class is public for serialization purposes.
 */
class OpaqueBytesSubSequence(private val bytes: ByteArray, offset: Int, size: Int) :
    ByteSequence(bytes, offset, size) {
    init {
        require(offset >= 0 && offset < bytes.size) {
            "Offset must be greater than or equal to 0, and less than the size of the backing array"
        }
        require(size >= 0 && offset + size <= bytes.size) {
            "Sub-sequence size must be greater than or equal to 0, and less than the size of the backing array"
        }
    }

    override fun getBytes() = bytes
}

/**
 * Wrap [size] bytes from this [ByteArray] starting from [offset] into a new [ByteArray].
 */
fun ByteArray.sequence(offset: Int = 0, size: Int = this.size) = OpaqueBytesSubSequence(this, offset, size)
