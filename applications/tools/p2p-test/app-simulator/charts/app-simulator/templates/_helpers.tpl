{{/*
Expand the name of the chart.
*/}}
{{- define "appSimulator.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "appSimulator.fullname" -}}
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
{{- define "appSimulator.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "appSimulator.labels" -}}
helm.sh/chart: {{ include "appSimulator.chart" . }}
{{ include "appSimulator.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "appSimulator.selectorLabels" -}}
app.kubernetes.io/name: {{ include "appSimulator.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Image pull secrets
*/}}
{{- define "appSimulator.imagePullSecrets" -}}
{{- if ( not ( empty  .Values.imagePullSecrets ) ) }}
imagePullSecrets:
{{- range .Values.imagePullSecrets }}
  - name: {{ . }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Type name
*/}}
{{- define "appSimulator.typeName" -}}
"{{ include "appSimulator.fullname" . }}-{{ .type | kebabcase }}"
{{- end }}

{{/*
Type common labels
*/}}
{{- define "appSimulator.typeLabels" -}}
{{ include "appSimulator.labels" . }}
{{ include "appSimulator.typeComponentLabel" . }}
{{- end }}

{{/*
Type selector labels
*/}}
{{- define "appSimulator.typeSelectorLabels" -}}
{{ include "appSimulator.selectorLabels" . }}
{{ include "appSimulator.typeComponentLabel" . }}
{{- end }}

{{/*
Type component label
*/}}
{{- define "appSimulator.typeComponentLabel" -}}
app.kubernetes.io/component: {{ .type }}
{{- end }}

{{/*
AppSimulator image
*/}}
{{- define "appSimulator.image" -}}
"{{ .Values.image.registry }}/{{ .Values.image.repository }}:{{ .Values.image.tag | default .Chart.AppVersion }}"
{{- end }}

{{/*
Kafka bootstrap servers
*/}}
{{- define "appSimulator.kafkaBootstrapServers" -}}
{{ required "Must specify kafka.bootstrapServers" .Values.kafka.bootstrapServers }}
{{- end }}

{{/*
Kafka arguments
*/}}
{{- define "appSimulator.kafkaArgs" -}}
- "-mbootstrap.servers={{ include "appSimulator.kafkaBootstrapServers" . }}"
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