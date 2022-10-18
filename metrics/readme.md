# Corda Metrics

Corda exposes metrics the Prometheus format.

The docker-compose configuration in this directory can be used to test this in the combined-worker, for example.
To start Prometheus and Grafana, simply run `docker compose up` in this directory and browse to the grafana dashboard by
on to `http://localhost:3000/` using the initial username & password of admin/admin. 

Prometheus is using the worker's `/metrics` endpoint exposed on port `7000` (default).

## Setting up Grafana for the first time

When running Grafana for the first time, it will not be configured. To do this, follow these steps:

* Configure Prometheus datasource:
  * Choose add datasource
  * Select Prometheus
  * Enter the Prometheus URL: `http://prometheus:9090`
* Visualise the metrics:
  * We can use the [official Micrometer dashboard](https://grafana.com/grafana/dashboards/4701-jvm-micrometer/) for visualising JVM Metrics.
  * From the Grafana homepage, choose `Dashboards` -> `+ Import`
  * Enter ID `4701` and click `Load`
  * Choose the Prometheus datasource
  * Click Import
  * The JVM metrics should now be available on the dashboard
* Custom metrics can be added to a dashboard by choosing the metrics name from the datasource. E.g. `http_server_requests_total`

