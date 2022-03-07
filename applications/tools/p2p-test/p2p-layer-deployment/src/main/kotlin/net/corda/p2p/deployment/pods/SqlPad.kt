package net.corda.p2p.deployment.pods

import com.fasterxml.jackson.databind.ObjectMapper

class SqlPad(
    username: String,
    password: String,
    dbHost: String,
    namespace: String,
) : Pod() {
    override val app = "db-ui"
    override val image = "sqlpad/sqlpad"
    override val ports = listOf(Port.Http)
    override val environmentVariables = mapOf(
        "PUBLIC_URL" to "http://db-ui.$namespace",
        "SQLPAD_PORT" to "80",
        "SQLPAD_AUTH_DISABLED" to "true",
        "SQLPAD_AUTH_DISABLED_DEFAULT_ROLE" to "admin",
        "SQLPAD_APP_LOG_LEVEL" to "info",
        "SQLPAD_SEED_DATA_PATH" to "/etc/sqlpad/seed-data",
        "SQLPAD_CONNECTIONS__corda__name" to "database",
        "SQLPAD_CONNECTIONS__corda__driver" to "postgres",
        "SQLPAD_CONNECTIONS__corda__host" to dbHost,
        "SQLPAD_CONNECTIONS__corda__database" to username,
        "SQLPAD_CONNECTIONS__corda__username" to username,
        "SQLPAD_CONNECTIONS__corda__password" to password,
    )
    private val defaultQueries = mapOf(
        "Sent messages" to "SELECT * FROM sent_messages",
        "Received messages" to "SELECT * FROM received_messages",
        "Count Missing messages" to "select to_char(sent.count, 'FM9,999,999') AS sent, \n" +
            "to_char(receive.count, 'FM9,999,999') AS received, \n" +
            "to_char(sent.count-receive.count, 'FM9,999,999') as missing \n" +
            " from (select count(*) as count from sent_messages) sent,  \n" +
            "(select count(*) as count from received_messages) receive",
        "Verify reliable delivery" to "select sm.sender_id, sm.message_id\n" +
            "from sent_messages sm \n" +
            "left join received_messages rm \n" +
            "on sm.sender_id = rm.sender_id and sm.message_id = rm.message_id \n" +
            "where rm.message_id is null\n",
        "Latency" to "select \n" +
            "\tto_timestamp(floor((extract('epoch' from rm.sent_timestamp) / 30 )) * 30)" +
            " at time zone 'utc' as time_window,\n" +
            "\tcount(rm.delivery_latency_ms) as total_messages,\n" +
            "\tmax(rm.delivery_latency_ms) as max_latency,\n" +
            "\tmin(rm.delivery_latency_ms) as min_latency,\n" +
            "\tavg(rm.delivery_latency_ms) as average_latency,\n" +
            "\tpercentile_disc(0.99) within group (order by rm.delivery_latency_ms) as p99_latency\n" +
            "from received_messages rm \n" +
            "group by time_window\n" +
            "order by time_window asc"
    )

    override val rawData by lazy {
        val jsonWriter = ObjectMapper().writer()
        listOf(
            TextRawData(
                name = "queries",
                dirName = "/etc/sqlpad/seed-data/queries",
                content = defaultQueries.map {
                    mapOf(
                        "name" to it.key,
                        "connectionId" to "corda",
                        "queryText" to it.value,
                        "createdBy" to "",
                        "acl" to listOf(
                            mapOf(
                                "groupId" to "__EVERYONE__",
                                "write" to false,
                            )
                        )
                    )
                }.mapIndexed { index: Int, data: Map<String, Any> ->
                    TextFile(
                        "query-$index.json",
                        jsonWriter.writeValueAsString(data + ("id" to "seed-query-$index"))
                    )
                }
            )
        )
    }

    override val readyLog = ".*Welcome to SQLPad.*".toRegex()
}
