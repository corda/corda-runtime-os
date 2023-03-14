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
{{- with .Chart.AppVersion }}
app.kubernetes.io/version: {{ . | quote }}
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
{{- define  "corda.imagePullSecrets" -}}
{{- with .Values.imagePullSecrets }}
imagePullSecrets:
{{- range . }}
  - name: {{ . }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Container security context
*/}}
{{- define "corda.containerSecurityContext" -}}
{{- if not .Values.dumpHostPath }}
securityContext:
  runAsUser: 10001
  runAsGroup: 10002
  allowPrivilegeEscalation: false
{{- end }}
{{- end }}

{{/*
Log4j volume
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
Kafka bootstrap servers
*/}}
{{- define "corda.kafkaBootstrapServers" -}}
{{ required "Must specify kafka.bootstrapServers" .Values.kafka.bootstrapServers }}
{{- end }}

{{/*
Initial admin user secret name
*/}}
{{- define "corda.initialAdminUserSecretName" -}}
{{ default (printf "%s-initial-admin-user" (include "corda.fullname" .)) }}
{{- end }}

{{/*
Initial admin user username secret name
*/}}
{{- define "corda.initialAdminUserUsernameSecretName" -}}
{{ .Values.bootstrap.initialAdminUser.username.valueFrom.secretKeyRef.name | default ((include "corda.initialAdminUserSecretName" .)) }}
{{- end }}

{{/*
Initial admin user password secret name
*/}}
{{- define "corda.initialAdminUserPasswordSecretName" -}}
{{ .Values.bootstrap.initialAdminUser.password.valueFrom.secretKeyRef.name | default (include "corda.initialAdminUserSecretName" .) }}
{{- end }}

{{/*
Initial admin user secret username key
*/}}
{{- define "corda.initialAdminUserSecretUsernameKey" -}}
{{- if .Values.bootstrap.initialAdminUser.username.valueFrom.secretKeyRef.name -}}
{{ required "Must specify bootstrap.initialAdminUser.username.valueFrom.secretKeyRef.key" .Values.bootstrap.initialAdminUser.username.valueFrom.secretKeyRef.key }}
{{- else -}}
username
{{- end -}}
{{- end -}}

{{/*
Initial admin user secret password key
*/}}
{{- define "corda.initialAdminUserSecretPasswordKey" -}}
{{- if .Values.bootstrap.initialAdminUser.password.valueFrom.secretKeyRef.name -}}
{{ required "Must specify bootstrap.initialAdminUser.password.valueFrom.secretKeyRef.key" .Values.bootstrap.initialAdminUser.password.valueFrom.secretKeyRef.key }}
{{- else -}}
password
{{- end -}}
{{- end -}}

{{/*
Initial admin secret environment variable
*/}}
{{- define "corda.initialAdminUserSecretEnv" -}}
- name: INITIAL_ADMIN_USER_USERNAME
  valueFrom:
    secretKeyRef:
      name: {{ include "corda.initialAdminUserUsernameSecretName" . }}
      key: {{ include "corda.initialAdminUserSecretUsernameKey" . }}
- name: INITIAL_ADMIN_USER_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ include "corda.initialAdminUserPasswordSecretName" . }}
      key: {{ include "corda.initialAdminUserSecretPasswordKey" . }}
{{- end -}}

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
Cluster DB name
*/}}
{{- define "corda.clusterDbName" -}}
{{- .Values.db.cluster.database | default "cordacluster" }}
{{- end -}}

{{/*
Default name for cluster DB secret
*/}}
{{- define "corda.clusterDbDefaultSecretName" -}}
{{ printf "%s-cluster-db" (include "corda.fullname" .) }}
{{- end -}}

{{/*
Cluster DB credentials environment variables
*/}}
{{- define "corda.clusterDbEnv" -}}
- name: DB_CLUSTER_USERNAME
  valueFrom:
    secretKeyRef:
      {{- if .Values.db.cluster.username.valueFrom.secretKeyRef.name }}
      name: {{ .Values.db.cluster.username.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify db.cluster.username.valueFrom.secretKeyRef.key" .Values.db.cluster.username.valueFrom.secretKeyRef.key | quote }}
      {{- else }}
      name: {{ include "corda.clusterDbDefaultSecretName" . | quote }}
      key: "username"
      {{- end }}
- name: DB_CLUSTER_PASSWORD
  valueFrom:
    secretKeyRef:
      {{- if .Values.db.cluster.password.valueFrom.secretKeyRef.name }}
      name: {{ .Values.db.cluster.password.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify db.cluster.password.valueFrom.secretKeyRef.key" .Values.db.cluster.password.valueFrom.secretKeyRef.key | quote }}
      {{- else }}
      name: {{ include "corda.clusterDbDefaultSecretName" . | quote }}
      key: "password"
      {{- end }}
{{- end -}}

{{/*
Default name for bootstrap cluster DB secret
*/}}
{{- define "corda.bootstrapClusterDbDefaultSecretName" -}}
{{ printf "%s-bootstrap-cluster-db" (include "corda.fullname" .) }}
{{- end -}}

{{/*
Bootstrap cluster DB credentials environment variables
*/}}
{{- define "corda.bootstrapClusterDbEnv" -}}
- name: PGUSER
  valueFrom:
    secretKeyRef:
      {{- if .Values.bootstrap.db.cluster.username.valueFrom.secretKeyRef.name }}
      name: {{ .Values.bootstrap.db.cluster.username.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify bootstrap.db.cluster.username.valueFrom.secretKeyRef.key" .Values.bootstrap.db.cluster.username.valueFrom.secretKeyRef.key | quote }}
      {{- else if .Values.bootstrap.db.cluster.username.value }}
      name: {{ include "corda.bootstrapClusterDbDefaultSecretName" . | quote }}
      key: "username"
      {{- else if .Values.db.cluster.username.valueFrom.secretKeyRef.name }}
      name: {{ .Values.db.cluster.username.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify db.cluster.username.valueFrom.secretKeyRef.key" .Values.db.cluster.username.valueFrom.secretKeyRef.key | quote }}
      {{- else }}
      name: {{ include "corda.clusterDbDefaultSecretName" . | quote }}
      key: "username"
      {{- end }}
- name: PGPASSWORD
  valueFrom:
    secretKeyRef:
      {{- if .Values.bootstrap.db.cluster.password.valueFrom.secretKeyRef.name }}
      name: {{ .Values.bootstrap.db.cluster.password.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify bootstrap.db.cluster.password.valueFrom.secretKeyRef.key" .Values.bootstrap.db.cluster.password.valueFrom.secretKeyRef.key | quote }}
      {{- else if .Values.bootstrap.db.cluster.password.value }}
      name: {{ include "corda.bootstrapClusterDbDefaultSecretName" . | quote }}
      key: "password"
      {{- else if .Values.db.cluster.password.valueFrom.secretKeyRef.name }}
      name: {{ .Values.db.cluster.password.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify db.cluster.password.valueFrom.secretKeyRef.key" .Values.db.cluster.password.valueFrom.secretKeyRef.key | quote }}
      {{- else}}
      name: {{ include "corda.clusterDbDefaultSecretName" . | quote }}
      key: "password"
      {{- end }}
{{- end -}}

{{/*
Kafka TLS truststore password
*/}}
{{- define "corda.kafkaTlsPassword" -}}
{{- if .Values.kafka.tls.enabled -}}
  {{- if .Values.kafka.tls.truststore.password.valueFrom.secretKeyRef.name -}}
- name: TRUSTSTORE_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ .Values.kafka.tls.truststore.password.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify kafka.tls.truststore.password.valueFrom.secretKeyRef.key" .Values.kafka.tls.truststore.password.valueFrom.secretKeyRef.key | quote }}
  {{- else if .Values.kafka.tls.truststore.password.value -}}
- name: TRUSTSTORE_PASSWORD
  value: {{ .Values.kafka.tls.truststore.password.value | quote }}
  {{- end -}}
{{- end -}}
{{- end -}}

{{/*
Bootstrap Kafka SASL username and password environment variables
*/}}
{{- define "corda.bootstrapKafkaSaslUsernameAndPasswordEnv" -}}
{{- if .Values.kafka.sasl.enabled }}
- name: SASL_USERNAME
  {{- if .Values.bootstrap.kafka.sasl.username.valueFrom.secretKeyRef.name -}}
  valueFrom:
    secretKeyRef:
      name: {{ .Values.bootstrap.kafka.sasl.username.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify bootstrap.kafka.sasl.username.valueFrom.secretKeyRef.key" .Values.bootstrap.kafka.sasl.username.valueFrom.secretKeyRef.key | quote }}
  {{- else if .Values.bootstrap.kafka.sasl.username.value }}
  value: {{ .Values.bootstrap.kafka.sasl.username.value | quote }}
  {{- else if .Values.kafka.sasl.username.valueFrom.secretKeyRef.name }}
  valueFrom:
    secretKeyRef:
      name: {{ .Values.kafka.sasl.username.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify kafka.sasl.username.valueFrom.secretKeyRef.key" .Values.kafka.sasl.username.valueFrom.secretKeyRef.key | quote }}
  {{- else }}
  value: {{ required "Must specify bootstrap.kafka.sasl.username.value, bootstrap.kafka.sasl.username.valueFrom.secretKeyRef.name, kafka.sasl.username.value, or kafka.sasl.username.valueFrom.secretKeyRef.name" .Values.kafka.sasl.username.value }}
  {{- end }}
- name: SASL_PASSWORD
  {{- if .Values.bootstrap.kafka.sasl.password.valueFrom.secretKeyRef.name }}
  valueFrom:
    secretKeyRef:
      name: {{ .Values.bootstrap.kafka.sasl.password.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify kafka.sasl.password.valueFrom.secretKeyRef.key" .Values.bootstrap.kafka.sasl.password.valueFrom.secretKeyRef.key | quote }}
  {{- else if .Values.bootstrap.kafka.sasl.password.value }}
  value: {{ .Values.bootstrap.kafka.sasl.password.value | quote }}
  {{- else if .Values.kafka.sasl.password.valueFrom.secretKeyRef.name }}
  valueFrom:
    secretKeyRef:
      name: {{ .Values.kafka.sasl.password.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify kafka.sasl.password.valueFrom.secretKeyRef.key" .Values.kafka.sasl.password.valueFrom.secretKeyRef.key | quote }}
  {{- else }}
  value: {{ required "Must specify bootstrap.kafka.sasl.password.value, bootstrap.kafka.sasl.password.valueFrom.secretKeyRef.name, kafka.sasl.password.value, or kafka.sasl.password.valueFrom.secretKeyRef.name" .Values.kafka.sasl.password.value }}
  {{- end }}
{{- end }}
{{- end -}}

{{/*
SALT and PASSPHRASE environment variables for decrypting configuration.
*/}}
{{- define "corda.configSaltAndPassphraseEnv" -}}
- name: SALT
  valueFrom:
    secretKeyRef:
      {{- if .Values.config.encryption.salt.valueFrom.secretKeyRef.name }}
      name: {{ .Values.config.encryption.salt.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify config.encryption.salt.valueFrom.secretKeyRef.key" .Values.config.encryption.salt.valueFrom.secretKeyRef.key | quote }}
      {{- else }}
      name: {{ (printf "%s-config" (include "corda.fullname" .)) | quote }}
      key: "salt"
      {{- end }}
- name: PASSPHRASE
  valueFrom:
    secretKeyRef:
      {{- if .Values.config.encryption.passphrase.valueFrom.secretKeyRef.name }}
      name: {{ .Values.config.encryption.passphrase.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify config.encryption.passphrase.valueFrom.secretKeyRef.key" .Values.workers.db.passphrase.valueFrom.secretKeyRef.key | quote }}
      {{- else }}
      name: {{ (printf "%s-config" (include "corda.fullname" .)) | quote }}
      key: "passphrase" 
      {{- end }}
{{- end }}

{{/*
Default name for RBAC DB secret
*/}}
{{- define "corda.rbacDbDefaultSecretName" -}}
{{ printf "%s-rbac-db" (include "corda.fullname" .) }}
{{- end -}}

{{/*
RBAC user environment variable
*/}}
{{- define "corda.rbacDbUserEnv" -}}
- name: RBAC_DB_USER_USERNAME
  valueFrom:
    secretKeyRef:
      {{- if .Values.bootstrap.db.rbac.username.valueFrom.secretKeyRef.name }}
      name: {{ .Values.bootstrap.db.rbac.username.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify bootstrap.db.rbac.username.valueFrom.secretKeyRef.key" .Values.bootstrap.db.rbac.username.valueFrom.secretKeyRef.key | quote }}
      {{- else }}
      name: {{ include "corda.rbacDbDefaultSecretName" . | quote }}
      key: "username" 
      {{- end }}
- name: RBAC_DB_USER_PASSWORD
  valueFrom:
    secretKeyRef:
      {{- if .Values.bootstrap.db.rbac.password.valueFrom.secretKeyRef.name }}
      name: {{ .Values.bootstrap.db.rbac.password.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify bootstrap.db.rbac.password.valueFrom.secretKeyRef.key" .Values.bootstrap.db.rbac.password.valueFrom.secretKeyRef.key | quote }}
      {{- else }}
      name: {{ include "corda.rbacDbDefaultSecretName" . | quote }}
      key: "password" 
      {{- end }}
{{- end -}}

{{/*
Default name for crypto DB secret
*/}}
{{- define "corda.cryptoDbDefaultSecretName" -}}
{{ printf "%s-crypto-db" (include "corda.fullname" .) }}
{{- end -}}

{{/*
Crypto worker environment variable
*/}}
{{- define "corda.cryptoDbUserEnv" -}}
- name: CRYPTO_DB_USER_USERNAME
  valueFrom:
    secretKeyRef:
      {{- if .Values.bootstrap.db.crypto.username.valueFrom.secretKeyRef.name }}
      name: {{ .Values.bootstrap.db.crypto.username.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify bootstrap.db.crypto.username.valueFrom.secretKeyRef.key" .Values.bootstrap.db.crypto.username.valueFrom.secretKeyRef.key | quote }}
      {{- else }}
      name: {{ include "corda.cryptoDbDefaultSecretName" . | quote }}
      key: "username" 
      {{- end }}
- name: CRYPTO_DB_USER_PASSWORD
  valueFrom:
    secretKeyRef:
      {{- if .Values.bootstrap.db.crypto.password.valueFrom.secretKeyRef.name }}
      name: {{ .Values.bootstrap.db.crypto.password.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify bootstrap.db.crypto.password.valueFrom.secretKeyRef.key" .Values.bootstrap.db.crypto.password.valueFrom.secretKeyRef.key | quote }}
      {{- else }}
      name: {{ include "corda.cryptoDbDefaultSecretName" . | quote }}
      key: "password" 
      {{- end }}
{{- end -}}

{{/*
Config map for Log4J configuration
*/}}
{{- define "corda.log4jConfigMap" -}}
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ printf "%s-log4j" (include "corda.fullname" . ) }}
  annotations:
    "helm.sh/hook-weight": "-1"
    "helm.sh/hook": pre-install
  labels:
    {{- include "corda.labels" . | nindent 4 }}
data:
  log4j2.xml: | {{ $.Files.Get "log4j2.xml" | nindent 4 }}
  {{- range $k, $v := .Values.workers }}
  {{- if $v.logging.override }}
  log4j2-{{ $k }}.xml: |
    {{- $v.logging.override | nindent 4 }}
  {{- end }}
  {{- end }}
{{- end }}

{{/*
Secret creation
*/}}
{{- define "corda.secret" -}}
{{- $ := index . 0 }}
{{- $context := index . 1 }}
{{- $path := index . 2 }}
{{- $secretName := index . 3 }}
{{- $fields := index . 4 }}
{{- with $context }}
{{- $create := false }}
{{- range $k, $v := $fields }}
{{-   $field := ( get $context $k ) }}
{{-   if not $field.valueFrom.secretKeyRef.name }}
{{-     if and $v.required ( not $field.value ) }}
{{-       fail ( printf "Must specify %s.%s.valueFrom.secretKeyRef.name or %s.%s.value" $path $k $path $k ) }}
{{-     end }}
{{-     if or $field.value $v.generate }}
{{-       $create = true }}
{{-     end }}
{{-   end }}
{{- end }}
{{- if $create }}
---
apiVersion: v1
kind: Secret
metadata:
  name: {{ $secretName }}
  annotations:
    "helm.sh/hook-weight": "-1"
    "helm.sh/hook": pre-install
  labels:
    {{- include "corda.labels" $ | nindent 4 }}
type: Opaque
data:
{{- range $k, $v := $fields }}
{{-   $field := ( get $context $k ) }}
{{-   if not $field.valueFrom.secretKeyRef.name }}
{{-     if $field.value }}
  {{ $k }}: {{ $field.value | b64enc | quote }}
{{-     else if $v.generate }}
{{-       $existingSecret := lookup "v1" "Secret" $.Release.Namespace $secretName }}
{{-       $existingValue := "" }}
{{-       if $existingSecret }}
{{-         $existingValue = index $existingSecret.data $k }}
{{-       end }}
{{-       if $existingValue }}
  {{ $k }}: {{ $existingValue }}
{{-       else }}
  {{ $k }}: {{ randAlphaNum $v.generate | b64enc | quote }}
{{-       end }}
{{-     end }}
{{-   end }}
{{- end }}
{{- end }}
{{- end }}
{{- end }}
