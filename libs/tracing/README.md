# Tracing

This module allows us to give each REST request a unique trace ID that is logged with every log message related to that request.
It also supports submitting the trace to a Zipkin server that collects data for presenting via a dashboard.

Tracing is made up of two IDs `traceId` and `spanId`.
- `traceId` is the unique identifier for the request and remains the same throughout all work for that request.
- `spanId` is nested within a `traceId` and represents a piece of work we want to measure, there can be multiple spans within a request

## Configuration

The system will include these two IDs inside the loging MDC allowing us to tie all the log messages to a request.

To feed data into a dashboard you need to set the `CORDA_TRACING_SERVER_ZIPKIN_PROTOCOL` environment variable.

```shell
CORDA_TRACING_SERVER_ZIPKIN_PROTOCOL=http://localhost:9411
```

## Providing IDs on REST requests

The trace ID can be provided to the REST endpoint in HTTP headers.
Both headers must be provided otherwise the system will treat the ID as missing and generate a new ID.

- `X-B3-TraceId` - The trace ID as a 64 or 128bit binary number encoded in hexadecimal
- `X-B3-SpanId` - The span ID as a 64bit binary number encoded in hexadecimal

```shell
TRACE_ID=`openssl rand -hex 16` # 16 bytes, 128 bits
SPAN_ID=`openssl rand -hex 8`   #  8 bytes,  64 bits
curl --insecure -u admin:admin --header "X-B3-TraceId: $TRACE_ID" --header "X-B3-SpanId: $SPAN_ID"  https://localhost:8888/api/v1/flow/$HOLDING_ID/r1
```

