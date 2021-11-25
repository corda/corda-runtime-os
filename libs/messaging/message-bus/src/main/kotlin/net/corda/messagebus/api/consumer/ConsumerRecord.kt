/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.corda.messagebus.api.consumer;

/**
 * A key/value pair to be received from Kafka. This also consists of a topic name and 
 * a partition number from which the record is being received, an offset that points 
 * to the record in a Kafka partition, and a timestamp as marked by the corresponding ProducerRecord.
 */
interface ConsumerRecord<K, V> {

    fun topic(): String

    /**
     * The partition from which this record is received
     */
    fun partition(): Int

    /**
     * The key (or null if no key is specified)
     */
    fun key(): K

    /**
     * The value
     */
    fun value(): V

    /**
     * The position of this record in the corresponding Kafka partition.
     */
    fun offset(): Long

    /**
     * The timestamp of this record
     */
    fun timestamp(): Long
}
