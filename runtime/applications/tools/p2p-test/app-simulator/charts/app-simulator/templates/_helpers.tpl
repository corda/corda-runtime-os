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
{{- if .Values.kafka.tls.truststore.valueFrom.secretKeyRef.name }}
- "-mssl.truststore.location=/certs/ca.crt"
- "-mssl.truststore.type={{ .Values.kafka.tls.truststore.type | upper }}"
{{- if or .Values.kafka.tls.truststore.password.value .Values.kafka.tls.truststore.password.valueFrom.secretKeyRef.name }}
- "-mssl.truststore.password=$TRUSTSTORE_PASSWORD"
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
Database arguments
*/}}
{{- define "appSimulator.databaseArgs" -}}
- "-dusername={{ .Values.db.appSimulator.user }}"
- "-dpassword={{ .Values.db.appSimulator.password }}"
- "-ddb={{ .Values.db.appSimulator.database }}"
- "-dhost={{ .Values.db.appSimulator.host }}.{{ .Values.db.appSimulator.namespace }}"
{{- end }}

{{/*
CLI image
*/}}
{{- define "appSimulator.bootstrapImage" -}}
"{{ .Values.bootstrap.image.registry | default .Values.image.registry }}/{{ .Values.bootstrap.image.repository }}:{{ .Values.bootstrap.image.tag | default .Values.image.tag | default .Chart.AppVersion }}"
{{- end }}

{{/*
Volume mounts for the appSimulator
*/}}
{{- define "appSimulator.volumeMounts" }}
{{- if and .Values.kafka.tls.enabled .Values.kafka.tls.truststore.valueFrom.secretKeyRef.name }}
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
{{- end }}

{{/*
Volumes for the appSimulator
*/}}
{{- define "appSimulator.volumes" }}
{{- if and .Values.kafka.tls.enabled .Values.kafka.tls.truststore.valueFrom.secretKeyRef.name }}
- name: certs
  secret:
    secretName: {{ .Values.kafka.tls.truststore.valueFrom.secretKeyRef.name | quote }}
    items:
      - key: {{ .Values.kafka.tls.truststore.valueFrom.secretKeyRef.key | quote }}
        path: "ca.crt"
{{- end -}}
{{- if .Values.kafka.sasl.enabled  }}
- name: jaas-conf
  emptyDir: {}
{{- end }}
{{- end }}

{{/*
Kafka SASL username and password environment variables
*/}}
{{- define "appSimulator.kafkaSaslUsernameAndPasswordEnv" -}}
{{- if .Values.kafka.sasl.enabled }}
  {{- $username := .Values.appSimulators.kafka.sasl.username }}
- name: SASL_USERNAME
  {{- if $username.valueFrom.secretKeyRef.name }}
  valueFrom:
    secretKeyRef:
      name: {{ $username.valueFrom.secretKeyRef.name | quote }}
      key: {{ required (printf "Must specify .Values.appSimulators.kafka.sasl.username.valueFrom.secretKeyRef.key") $username.valueFrom.secretKeyRef.key | quote }}
  {{- else if $username.value }}
  value: {{ $username.value | quote }}
  {{- else if .Values.kafka.sasl.username.valueFrom.secretKeyRef.name }}
  valueFrom:
    secretKeyRef:
      name: {{ .Values.kafka.sasl.username.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify kafka.sasl.username.valueFrom.secretKeyRef.key" .Values.kafka.sasl.username.valueFrom.secretKeyRef.key | quote }}
  {{- else }}
  value: {{ required (printf "Must specify .Values.appSimulators.kafka.sasl.username.value, appSimulators.kafka.sasl.username.valueFrom.secretKeyRef.name, kafka.sasl.username.value, or kafka.sasl.username.valueFrom.secretKeyRef.name") .Values.kafka.sasl.username.value }}
  {{- end }}
  {{- $password := .Values.appSimulators.kafka.sasl.password }}
- name: SASL_PASSWORD
  {{- if $password.valueFrom.secretKeyRef.name }}
  valueFrom:
    secretKeyRef:
      name: {{ $password.valueFrom.secretKeyRef.name | quote }}
      key: {{ required (printf "Must specify .Values.appSimulators.kafka.sasl.password.valueFrom.secretKeyRef.key") $password.valueFrom.secretKeyRef.key | quote }}
  {{- else if $password.value }}
  value: {{ $password.value | quote }}
  {{- else if .Values.kafka.sasl.password.valueFrom.secretKeyRef.name }}
  valueFrom:
    secretKeyRef:
      name: {{ .Values.kafka.sasl.password.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify kafka.sasl.password.valueFrom.secretKeyRef.key" .Values.kafka.sasl.password.valueFrom.secretKeyRef.key | quote }}
  {{- else }}
  value: {{ required (printf "Must specify .Values.appSimulators.kafka.sasl.password.value, .Values.appSimulators.kafka.sasl.password.valueFrom.secretKeyRef.name, kafka.sasl.password.value, or kafka.sasl.password.valueFrom.secretKeyRef.name") .Values.kafka.sasl.password.value }}
  {{- end }}
{{- end }}
{{- end -}}


{{/*
Kafka SASL init container
*/}}
{{- define "appSimulator.kafkaSaslInitContainer" -}}
{{- if .Values.kafka.sasl.enabled }}
- name: create-sasl-jaas-conf
  image: {{ include "appSimulator.image" . }}
  imagePullPolicy:  {{ .Values.imagePullPolicy }}
  env:
  {{- include "appSimulator.kafkaSaslUsernameAndPasswordEnv" . | nindent 2 }}
  command:
  - /bin/bash
  - -c
  args:
    - |
        cat <<EOF > /etc/config/jaas.conf
        KafkaClient {
            {{- if eq .Values.kafka.sasl.mechanism "PLAIN" }}
            org.apache.kafka.common.security.plain.PlainLoginModule required
            {{- else }}
            org.apache.kafka.common.security.scram.ScramLoginModule required
            {{- end }}
            username="$SASL_USERNAME"
            password="$SASL_PASSWORD";
        };    
        EOF
  volumeMounts:
  - mountPath: "/etc/config"
    name: "jaas-conf"
    readOnly: false
  resources:
  {{- toYaml .Values.resources | nindent 3 }}
{{- end }}    
{{- end }}
