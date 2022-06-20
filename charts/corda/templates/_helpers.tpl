{{/*
Expand the name of the chart.
*/}}
{{- define "corda.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "corda.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "corda.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "corda.labels" -}}
helm.sh/chart: {{ include "corda.chart" . }}
{{ include "corda.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "corda.selectorLabels" -}}
app.kubernetes.io/name: {{ include "corda.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Image pull secrets
*/}}
{{- define "corda.imagePullSecrets" -}}
{{- if ( not ( empty  .Values.imagePullSecrets ) ) }}
imagePullSecrets:
{{- range .Values.imagePullSecrets }}
  - name: {{ . }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Worker name
*/}}
{{- define "corda.workerName" -}}
"{{ include "corda.fullname" . }}-{{ .worker }}-worker"
{{- end }}

{{/*
Worker common labels
*/}}
{{- define "corda.workerLabels" -}}
{{ include "corda.labels" . }}
{{ include "corda.workerComponentLabel" . }}
{{- end }}

{{/*
Worker selector labels
*/}}
{{- define "corda.workerSelectorLabels" -}}
{{ include "corda.selectorLabels" . }}
{{ include "corda.workerComponentLabel" . }}
{{- end }}

{{/*
Worker component label
*/}}
{{- define "corda.workerComponentLabel" -}}
app.kubernetes.io/component: {{ .worker }}-worker
{{- end }}

{{/*
Worker image
*/}}
{{- define "corda.workerImage" -}}
"{{ ( get .Values.workers .worker ).image.registry | default .Values.image.registry }}/{{ ( get .Values.workers .worker ).image.repository }}:{{ ( get .Values.workers .worker ).image.tag | default .Values.image.tag | default .Chart.AppVersion }}"
{{- end }}

{{/*
CLI image
*/}}
{{- define "corda.bootstrapImage" -}}
"{{ .Values.bootstrap.image.registry | default .Values.image.registry }}/{{ .Values.bootstrap.image.repository }}:{{ .Values.bootstrap.image.tag | default .Values.image.tag | default .Chart.AppVersion }}"
{{- end }}

{{/*
DB client image
*/}}
{{- define "corda.dbClientImage" -}}
"{{- if .Values.db.clientImage.registry }}{{.Values.db.clientImage.registry}}/{{- end }}{{ .Values.db.clientImage.repository }}:{{ .Values.db.clientImage.tag }}"
{{- end }}

{{/*
Kafka client image
*/}}
{{- define "corda.kafkaClientImage" -}}
"{{- if .Values.kafka.clientImage.registry }}{{.Values.kafka.clientImage.registry}}/{{- end }}{{ .Values.kafka.clientImage.repository }}:{{ .Values.kafka.clientImage.tag }}"
{{- end }}

{{/*
Worker JAVA_TOOL_OPTIONS
*/}}
{{- define "corda.workerJavaToolOptions" -}}
{{- if ( get .Values.workers .worker ).debug.enabled -}}
- name: JAVA_TOOL_OPTIONS
  value: -agentlib:jdwp=transport=dt_socket,server=y,address=5005,suspend={{ if ( get .Values.workers .worker ).debug.suspend }}y{{ else }}n{{ end }}{{- if .Values.kafka.sasl.enabled -}} -Djava.security.auth.login.config=/etc/config/jaas.conf {{ end }}
{{- else -}}
{{- if .Values.kafka.sasl.enabled -}}
- name: JAVA_TOOL_OPTIONS
  value: -Djava.security.auth.login.config=/etc/config/jaas.conf 
{{- end }}
{{- end }}
{{- end }}

{{/*
Kafka bootstrap servers
*/}}
{{- define "corda.kafkaBootstrapServers" -}}
{{ required "Must specify kafka.bootstrapServers" .Values.kafka.bootstrapServers }}
{{- end }}

{{/*
Worker Kafka arguments
*/}}
{{- define "corda.workerKafkaArgs" -}}
- "-mbootstrap.servers={{ include "corda.kafkaBootstrapServers" . }}"
- "--topicPrefix={{ .Values.kafka.topicPrefix }}"
{{- if .Values.kafka.tls.enabled }}
{{- if .Values.kafka.sasl.enabled }}
- "-msecurity.protocol=SASL_SSL"
- "-msasl.mechanism={{ .Values.kafka.sasl.mechanism }}"
{{- else }}
- "-msecurity.protocol=SSL"
{{- end }}
{{- if .Values.kafka.tls.truststore.secretRef.name }}
- "-mssl.truststore.location=/certs/ca.crt"
- "-mssl.truststore.type={{ .Values.kafka.tls.truststore.type | upper }}"
{{- if .Values.kafka.tls.truststore.password }}
- "-mssl.truststore.password={{ .Values.kafka.tls.truststore.password }}"
{{- end }}
{{- end }}
{{- else }}
{{- if .Values.kafka.sasl.enabled }}
- "-msecurity.protocol=SASL_PLAINTEXT"
- "-msasl.mechanism={{ .Values.kafka.sasl.mechanism }}"
{{- end }}
{{- end }}
{{- end }}

{{/*
Resources for corda workers
*/}}
{{- define "corda.workerResources" }}

resources:
  requests:
  {{- if or .Values.resources.requests.cpu ( get .Values.workers .worker ).resources.requests.cpu }}
    cpu: {{ default .Values.resources.requests.cpu ( get .Values.workers .worker ).resources.requests.cpu }}

  {{- end }}
  {{- if or .Values.resources.requests.memory ( get .Values.workers .worker ).resources.requests.memory }}
    memory: {{ default .Values.resources.requests.memory ( get .Values.workers .worker ).resources.requests.memory }}
  {{- end}}
  limits:
  {{- if or .Values.resources.limits.cpu ( get .Values.workers .worker ).resources.limits.cpu }}
    cpu: {{ default .Values.resources.limits.cpu ( get .Values.workers .worker ).resources.limits.cpu }}

  {{- end }}
  {{- if or .Values.resources.limits.memory ( get .Values.workers .worker ).resources.limits.memory }}
    memory: {{ default .Values.resources.limits.memory ( get .Values.workers .worker ).resources.limits.memory }}  
  {{- end }} 
{{- end }}

{{/*
Volume mounts for corda workers
*/}}
{{- define "corda.workerVolumeMounts" }}
{{- if and .Values.kafka.tls.enabled .Values.kafka.tls.truststore.secretRef.name }}
- mountPath: "/certs"
  name: "certs"
  readOnly: true
{{- end }}
{{- if .Values.kafka.sasl.enabled  }}
- mountPath: "/etc/config"
  name: "jaas-conf"
  readOnly: true
{{- end }}
{{- end }}

{{/*
Volumes for corda workers
*/}}
{{- define "corda.workerVolumes" }}
{{- if and .Values.kafka.tls.enabled .Values.kafka.tls.truststore.secretRef.name }}
- name: certs
  secret:
    secretName: {{ .Values.kafka.tls.truststore.secretRef.name | quote }}
    items:
      - key: {{ .Values.kafka.tls.truststore.secretRef.key | quote }}
        path: "ca.crt"
{{- end -}}
{{- if .Values.kafka.sasl.enabled  }}
- name: jaas-conf
  secret:
    secretName: {{ include "corda.fullname" . }}-kafka-sasl
{{- end }}
{{- end }}

{{/*
Cluster DB type
*/}}
{{- define "corda.clusterDbType" -}}
{{- .Values.db.cluster.type | default "postgresql" }}
{{- end -}}

{{/*
Cluster DB port
*/}}
{{- define "corda.clusterDbPort" -}}
{{- .Values.db.cluster.port | default "5432" }}
{{- end -}}

{{/*
Cluster DB user
*/}}
{{- define "corda.clusterDbUser" -}}
{{- .Values.db.cluster.user | default "user" }}
{{- end -}}

{{/*
Cluster DB name
*/}}
{{- define "corda.clusterDbName" -}}
{{- .Values.db.cluster.database | default "cordacluster" }}
{{- end -}}

{{/*
Cluster DB secret name
*/}}
{{- define "corda.clusterDbSecretName" -}}
{{- .Values.db.cluster.existingSecret | default ( printf "%s-cluster-db" (include "corda.fullname" .) ) }}
{{- end -}}
