# Corda Metrics

Corda exposes metrics the Prometheus format.

The docker-compose configuration in this directory can be used to test this in the combined-worker, for example.
To start Prometheus and Grafana, simply run `docker compose up` in this directory and browse to the grafana dashboard by
on to `http://localhost:3000/` using the initial username & password of admin/admin. 

Prometheus is using the worker's `/metrics` endpoint exposed on the configured worker port (default `7000`, or `7004` for the combined worker by default).

## Grafana datasource

When running Grafana for the first time, it will be pre-configured with the prometheus datasource defined in 
`grafana/provisioning/datasources/datasource.yaml`

## Grafana dashboards

When running Grafana for the first time, it will be pre-configured with the 
[official Micrometer JVM dashboard](https://grafana.com/grafana/dashboards/4701-jvm-micrometer/), a copy of which resides in
`grafana/provisioning/dashboards/jvm-micrometer_rev9.json`.

This can be found by going to `Dashboards` -> `Browse`, then searching for `JVM`. The dashboard can be "starred" when it is opened.

Custom metrics can be added to a dashboard by choosing the metrics name from the datasource. E.g. `http_server_requests_total`.
An example Corda dashboard has been added (`grafana/provisioning/dashboards/corda.json`). Please note that this is for testing and 
development purpose and is not officially supported. Feel free to add/change/improve.
