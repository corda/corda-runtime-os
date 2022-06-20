package net.corda.v5.ledger.obsolete.crypto

/**
 * Constants used to configure the hashing functions used to calculate a transaction's Merkle tree
 */
object TransactionDigestType {
    /**
     * The default transaction for hashing the tree nodes
     */
    const val TREE: String = "TREE"

    /**
     * The indexer to the hash function for calculating the hash of the component
     */
    const val COMPONENTHASH: String = "COMPONENTHASH"

    /**
     * The indexer to the hash function for producing the NONCE
     */
    const val COMPONENTNONCE: String = "COMPONENTNONCE"

    /**
     * The indexer to the hash function for randomizing NONCEs (optional)
     */
    const val COMPONENTNONCEHASH: String = "COMPONENTNONCEHASH"
}
