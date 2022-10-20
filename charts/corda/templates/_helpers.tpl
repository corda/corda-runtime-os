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
{{ include "corda.fullname" . }}-{{ .worker | kebabcase | replace "p-2p" "p2p" }}-worker
{{- end }}

{{/*
Worker annotations
*/}}
{{- define "corda.workerAnnotations" -}}
{{ if .Values.metrics.scrape -}}
prometheus.io/scrape: "true"
prometheus.io/path: /metrics
prometheus.io/port: "7000"
{{- end }}
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
Worker security context
*/}}
{{- define "corda.workerSecurityContext" -}}
{{- if and ( not .Values.dumpHostPath ) ( not ( get .Values.workers .worker ).profiling.enabled ) }}
securityContext:
  runAsUser: 1000
  runAsGroup: 1000
  fsGroup: 1000
{{- end }}
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
Resources for the bootstrapper
*/}}
{{- define "corda.bootstrapResources" }}

resources:
  requests:
  {{- if or .Values.resources.requests.cpu .Values.bootstrap.resources.requests.cpu }}
    cpu: {{ default .Values.resources.requests.cpu .Values.bootstrap.resources.requests.cpu }}
  {{- end }}
  {{- if or .Values.resources.requests.memory .Values.bootstrap.resources.requests.memory }}
    memory: {{ default .Values.resources.requests.memory .Values.bootstrap.resources.requests.memory }}
  {{- end}}
  limits:
  {{- if or .Values.resources.limits.cpu .Values.bootstrap.resources.limits.cpu }}
    cpu: {{ default .Values.resources.limits.cpu .Values.bootstrap.resources.limits.cpu }}
  {{- end }}
  {{- if or .Values.resources.limits.memory .Values.bootstrap.resources.limits.memory }}
    memory: {{ default .Values.resources.limits.memory .Values.bootstrap.resources.limits.memory }}
  {{- end }}
{{- end }}

{{/*
Node selector for the bootstrapper
*/}}

{{- define "corda.bootstrapNodeSelector" }}
{{- with .Values.bootstrap.nodeSelector | default .Values.nodeSelector }}
nodeSelector:
  {{- toYaml . | nindent 2 }}
{{- end }}
{{- end }}

{{/*
Worker environment variables
*/}}
{{- define "corda.workerEnv" -}}
- name: K8S_NODE_NAME
  valueFrom:
    fieldRef:
      apiVersion: v1
      fieldPath: spec.nodeName
- name: K8S_POD_NAME
  valueFrom:
    fieldRef:
      apiVersion: v1
      fieldPath: metadata.name
- name: K8S_POD_UID
  valueFrom:
    fieldRef:
      apiVersion: v1
      fieldPath: metadata.uid
- name: K8S_NAMESPACE
  valueFrom:
    fieldRef:
      apiVersion: v1
      fieldPath: metadata.namespace
- name: JAVA_TOOL_OPTIONS
  value:
    {{ ( get .Values.workers .worker ).javaOptions }}
    {{- if ( get .Values.workers .worker ).debug.enabled }}
      -agentlib:jdwp=transport=dt_socket,server=y,address=5005,suspend={{ if ( get .Values.workers .worker ).debug.suspend }}y{{ else }}n{{ end }}
    {{- end -}}
    {{- if  ( get .Values.workers .worker ).profiling.enabled }}
      -agentpath:/opt/override/libyjpagent.so=exceptions=disable,port=10045,listen=all,dir=/dumps/profile/snapshots,logdir=/dumps/profile/logs
    {{- end -}}
    {{- if .Values.heapDumpOnOutOfMemoryError }}
      -XX:+HeapDumpOnOutOfMemoryError
      -XX:HeapDumpPath=/dumps/heap
    {{- end -}}
    {{- if ( get .Values.workers .worker ).verifyInstrumentation }}
      -Dco.paralleluniverse.fibers.verifyInstrumentation=true
    {{- end -}}
    {{- if .Values.kafka.sasl.enabled }}
      -Djava.security.auth.login.config=/etc/config/jaas.conf
    {{- end }}
- name: LOG4J_CONFIG_FILE
  {{- if  ( get .Values.workers .worker ).logging.override }}
  value: "/etc/log4j/log4j2.xml,/etc/log4j/log4j2-{{ .worker }}.xml"
  {{- else }}
  value: "/etc/log4j/log4j2.xml"
  {{- end }}
- name: CONSOLE_LOG_FORMAT
  value: {{ .Values.logging.format | quote }}
- name: CONSOLE_LOG_LEVEL
  value: {{ ( get .Values.workers .worker ).logging.level | default .Values.logging.level | quote }}
{{- end }}

{{/*
CLI log4j volume
*/}}
{{- define "corda.log4jVolume" -}}
- name: log4j
  configMap:
    name: {{ printf "%s-log4j" (include "corda.fullname" .) }}
{{- end }}

{{/*
Log4j volume mounts
*/}}
{{- define "corda.log4jVolumeMount" -}}
- name: log4j
  mountPath: /etc/log4j
{{- end }}

{{/*
Corda CLI environment variables
*/}}
{{- define "corda.cliEnv" -}}
- name: JAVA_TOOL_OPTIONS
  value: "-Dlog4j2.configurationFile=/etc/log4j/log4j2.xml"
- name: CONSOLE_LOG_FORMAT
  value: {{ .Values.logging.format }}
- name: CONSOLE_LOG_LEVEL
  value: {{ .Values.logging.level }}
{{- end }}

{{/*
Kafka bootstrap servers
*/}}
{{- define "corda.kafkaBootstrapServers" -}}
{{ required "Must specify kafka.bootstrapServers" .Values.kafka.bootstrapServers }}
{{- end }}

{{/*
Initial admin user secret name
*/}}
{{- define "corda.initialAdminUserSecretName" -}}
{{ .Values.bootstrap.initialAdminUser.secretRef.name | default (printf "%s-initial-admin-user" (include "corda.fullname" .)) }}
{{- end }}

{{/*
Initial admin user secret username key
*/}}
{{- define "corda.initialAdminUserSecretUsernameKey" -}}
{{ .Values.bootstrap.initialAdminUser.secretRef.usernameKey }}
{{- end }}

{{/*
Initial admin user secret password key
*/}}
{{- define "corda.initialAdminUserSecretPasswordKey" -}}
{{ .Values.bootstrap.initialAdminUser.secretRef.passwordKey }}
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
{{- define "corda.workerVolumeMounts" -}}
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
{{- if .Values.dumpHostPath }}
- mountPath: /dumps
  name: dumps
{{- end }}
{{ include "corda.log4jVolumeMount" . }}
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
{{- if .Values.dumpHostPath }}
- name: dumps
  hostPath:
    path: {{ .Values.dumpHostPath }}/{{ .Release.Namespace }}/{{ (include "corda.workerName" .) }}/
    type: DirectoryOrCreate
{{- end }}
{{ include "corda.log4jVolume" . }}
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
Cluster DB user environment variable 
*/}}
{{- define "corda.clusterDbUser" -}}
{{- if .Values.db.cluster.user.valueFrom.secretkeyRef.name }}
- name: PGUSER
  valueFrom:
    secretKeyRef:
      name: {{ .Values.db.cluster.user.valueFrom.secretkeyRef.name }}
      key: {{ .Values.db.cluster.user.valueFrom.secretkeyRef.usernameKey }}
{{- else }}
- name: PGUSER
  value: {{ .Values.db.cluster.user.value }}
{{- end }}
{{- end -}}

{{/*
Cluster DB name
*/}}
{{- define "corda.clusterDbName" -}}
{{- .Values.db.cluster.database | default "cordacluster" }}
{{- end -}}

{{/*
Cluster DB password secret 
*/}}
{{- define "corda.clusterDbSecretName" -}}
- name: PGPASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ .Values.db.cluster.password.valueFrom.secretkeyRef.name | default ( printf "%s-cluster-db" (include "corda.fullname" .) ) }}
      key: {{ .Values.db.cluster.password.valueFrom.secretkeyRef.passwordKey | default "password"}}
{{- end -}}

