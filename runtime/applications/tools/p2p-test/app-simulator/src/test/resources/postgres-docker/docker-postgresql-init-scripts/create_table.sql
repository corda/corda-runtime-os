CREATE TABLE sent_messages (
    sender_id varchar(512) NOT NULL,
    message_id varchar(512) NOT NULL,
    PRIMARY KEY(sender_id, message_id)
);

CREATE TABLE received_messages (
    sender_id varchar(512) NOT NULL,
    message_id varchar(512) NOT NULL,
    sent_timestamp timestamp,
    received_timestamp timestamp,
    delivery_latency_ms bigint,
    PRIMARY KEY(sender_id, message_id)
);