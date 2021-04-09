package api.samples.publisher

import api.samples.records.Record

interface Publisher<K, V> {

    /**
     * Return boolean success/failure to topic. 
     */
    fun publish(record: Record<K, V>) : Boolean
}