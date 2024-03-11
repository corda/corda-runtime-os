package net.corda.utilities

import org.xerial.snappy.Snappy

/**
 * Compress a ByteArray using Snappy
 */
fun ByteArray.compressSnappy(): ByteArray = Snappy.compress(this)

/**
 * Decompress a ByteArray using Snappy
 */
fun ByteArray.decompressSnappy(): ByteArray = Snappy.uncompress(this)