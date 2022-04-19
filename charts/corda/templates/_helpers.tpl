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
Worker JAVA_TOOL_OPTIONS
*/}}
{{- define "corda.workerJavaToolOptions" -}}
{{- if ( get .Values.workers .worker ).debug.enabled -}}
- name: JAVA_TOOL_OPTIONS
  value: -agentlib:jdwp=transport=dt_socket,server=y,address=5005,suspend={{ if ( get .Values.workers .worker ).debug.suspend }}y{{ else }}n{{ end }}
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
- -mbus.kafkaProperties.common.bootstrap.servers={{ include "corda.kafkaBootstrapServers" . }}
- -mbus.busType=KAFKA
- --topicPrefix={{ .Values.kafka.topicPrefix }}
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
Cluster DB secret name
*/}}
{{- define "corda.clusterDbSecretName" -}}
{{- .Values.db.cluster.existingSecret | default ( printf "%s-cluster-db" (include "corda.fullname" .) ) }}
{{- end -}}