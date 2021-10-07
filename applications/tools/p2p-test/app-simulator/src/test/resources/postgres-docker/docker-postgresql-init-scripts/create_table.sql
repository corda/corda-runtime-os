CREATE TABLE sent_messages (
    sender_id varchar(512) NOT NULL,
    message_id varchar(512) NOT NULL,
    sent_time timestamp,
    PRIMARY KEY(sender_id, message_id)
);

CREATE TABLE received_messages (
    sender_id varchar(512) NOT NULL,
    message_id varchar(512) NOT NULL,
    received_time timestamp,
    PRIMARY KEY(sender_id, message_id)
);