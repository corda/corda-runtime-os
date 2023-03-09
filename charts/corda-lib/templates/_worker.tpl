{{/*
Worker deployment.
*/}}
{{- define "corda.worker" -}}
{{- $ := index . 0 }}
{{- $worker := index . 2 }}
{{- $workerName := printf "%s-%s-worker" ( include "corda.fullname" $ ) ( include "corda.workerTypeKebabCase" $worker ) }}
{{- $optionalArgs := dict }}
{{- if gt (len .) 3 }}{{ $optionalArgs = index . 3 }}{{ end }}
{{- with index . 1 }}
{{- with .service }}
---
apiVersion: v1
kind: Service
metadata:
  name: {{ $workerName | quote }}
  labels:
    {{- include "corda.workerLabels" ( list $ $worker ) | nindent 4 }}
  {{- with .annotations }}
  annotations:
  {{- range $key, $value := . }}
    {{ $key }}: {{ $value | quote }}
  {{- end }}
  {{- end}}
spec:
  type: {{ .type }}
  {{- if .externalTrafficPolicy }}
  externalTrafficPolicy: {{ .externalTrafficPolicy }}
  {{- else if .loadBalancerSourceRanges }}
  loadBalancerSourceRanges: {{ .loadBalancerSourceRanges }}
  {{- end }}
  selector:
    {{- include "corda.workerSelectorLabels" ( list $ $worker ) | nindent 4 }}
  ports:
  - name: http
    port: {{ .port }}
    targetPort: http
{{- end }}
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ $workerName | quote }}
  labels:
    {{- include "corda.workerLabels" ( list $ $worker ) | nindent 4 }}
spec:
  replicas: {{ .replicaCount }}
  selector:
    matchLabels:
      {{- include "corda.workerSelectorLabels" ( list $ $worker ) | nindent 6 }}
  template:
    metadata:
      annotations:
        {{- if $.Values.metrics.scrape }}
        prometheus.io/scrape: "true"
        prometheus.io/path: /metrics
        prometheus.io/port: "7000"
        {{- end }}
        {{- with $.Values.annotations }}
        {{ . | toYaml }}
        {{- end }}
        {{- with .annotations }}
        {{ . | toYaml }}
        {{- end }}
      labels:
        {{- include "corda.workerSelectorLabels" ( list $ $worker ) | nindent 8 }}
    spec:
      {{- if and ( not $.Values.dumpHostPath ) ( not .profiling.enabled ) }}
      securityContext:
        runAsUser: 10001
        runAsGroup: 10002
        fsGroup: 1000
      {{- end }}
      {{- include "corda.imagePullSecrets" $ | nindent 6 }}
      {{- with $.Values.serviceAccount.name  }}
      serviceAccountName: {{ . }}
      {{- end }}
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
            - weight: 1
              podAffinityTerm:
                labelSelector:
                  matchExpressions:
                    - key: "app.kubernetes.io/component"
                      operator: In
                      values:
                        - {{ include "corda.workerComponent" $worker }}
                topologyKey: "kubernetes.io/hostname"
      containers:
      - name: {{ $workerName | quote }}
        image: {{ include "corda.workerImage" ( list $ . ) }}
        imagePullPolicy:  {{ $.Values.imagePullPolicy }}
        {{- if not .profiling.enabled }}
        {{- include "corda.containerSecurityContext" $ | nindent 8 }}
        {{- end }}
        resources:
          requests:
          {{- if or $.Values.resources.requests.cpu .resources.requests.cpu }}
            cpu: {{ default $.Values.resources.requests.cpu .resources.requests.cpu }}
          {{- end }}
          {{- if or $.Values.resources.requests.memory .resources.requests.memory }}
            memory: {{ default $.Values.resources.requests.memory .resources.requests.memory }}
          {{- end}}
          limits:
          {{- if or $.Values.resources.limits.cpu .resources.limits.cpu }}
            cpu: {{ default $.Values.resources.limits.cpu .resources.limits.cpu }}
          {{- end }}
          {{- if or $.Values.resources.limits.memory .resources.limits.memory }}
            memory: {{ default $.Values.resources.limits.memory .resources.limits.memory }}
          {{- end }}
        env:
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
              {{ .javaOptions }}
              {{- if .debug.enabled }}
                -agentlib:jdwp=transport=dt_socket,server=y,address=5005,suspend={{ if .debug.suspend }}y{{ else }}n{{ end }}
              {{- end -}}
              {{- if  .profiling.enabled }}
                -agentpath:/opt/override/libyjpagent.so=exceptions=disable,port=10045,listen=all,dir=/dumps/profile/snapshots,logdir=/dumps/profile/logs
              {{- end -}}
              {{- if $.Values.heapDumpOnOutOfMemoryError }}
                -XX:+HeapDumpOnOutOfMemoryError
                -XX:HeapDumpPath=/dumps/heap
              {{- end -}}
              {{- if .verifyInstrumentation }}
                -Dco.paralleluniverse.fibers.verifyInstrumentation=true
              {{- end -}}
              {{- if $.Values.kafka.sasl.enabled }}
                -Djava.security.auth.login.config=/etc/config/jaas.conf
              {{- end }}
          - name: LOG4J_CONFIG_FILE
            {{- if .logging.override }}
            value: "/etc/log4j/log4j2.xml,/etc/log4j/log4j2-{{ $worker }}.xml"
            {{- else }}
            value: "/etc/log4j/log4j2.xml"
            {{- end }}
          - name: CONSOLE_LOG_FORMAT
            value: {{ $.Values.logging.format | quote }}
          - name: CONSOLE_LOG_LEVEL
            value: {{ .logging.level | default $.Values.logging.level | quote }}
        {{- include "corda.configSaltAndPassphraseEnv" $ | nindent 10 }}
        {{- if $optionalArgs.clusterDbAccess }}
        {{- include "corda.clusterDbEnv" $ | nindent 10 }}
        {{- end }}
        args:
          - "-mbootstrap.servers={{ include "corda.kafkaBootstrapServers" $ }}"
          - "--topic-prefix={{ $.Values.kafka.topicPrefix }}"
          {{- if $.Values.kafka.tls.enabled }}
          {{- if $.Values.kafka.sasl.enabled }}
          - "-msecurity.protocol=SASL_SSL"
          - "-msasl.mechanism={{ $.Values.kafka.sasl.mechanism }}"
          {{- else }}
          - "-msecurity.protocol=SSL"
          {{- end }}
          {{- if $.Values.kafka.tls.truststore.valueFrom.secretKeyRef.name }}
          - "-mssl.truststore.location=/certs/ca.crt"
          - "-mssl.truststore.type={{ $.Values.kafka.tls.truststore.type | upper }}"
          {{- if or $.Values.kafka.tls.truststore.password.value $.Values.kafka.tls.truststore.password.valueFrom.secretKeyRef.name }}
          - "-mssl.truststore.password=$TRUSTSTORE_PASSWORD"
          {{- end }}
          {{- end }}
          {{- else }}
          {{- if $.Values.kafka.sasl.enabled }}
          - "-msecurity.protocol=SASL_PLAINTEXT"
          - "-msasl.mechanism={{ $.Values.kafka.sasl.mechanism }}"
          {{- end }}
          {{- end }}
          - "-spassphrase=$(PASSPHRASE)"
          - "-ssalt=$(SALT)"
          {{- if $optionalArgs.clusterDbAccess }}
          - "-ddatabase.user=$(DB_CLUSTER_USERNAME)"
          - "-ddatabase.pass=$(DB_CLUSTER_PASSWORD)"
          - "-ddatabase.jdbc.url=jdbc:postgresql://{{ required "Must specify db.cluster.host" $.Values.db.cluster.host }}:{{ $.Values.db.cluster.port }}/{{ $.Values.db.cluster.database }}?currentSchema={{ $.Values.db.cluster.schema }}"
          - "-ddatabase.jdbc.directory=/opt/jdbc-driver"
          - "-ddatabase.pool.max_size={{ .clusterDbConnectionPool.maxSize }}"
          {{- end }}
          {{- range $i, $arg := $optionalArgs.additionalWorkerArgs }}
          - {{ $arg | quote }}
          {{- end }}
        volumeMounts:
          {{- if and $.Values.kafka.tls.enabled $.Values.kafka.tls.truststore.valueFrom.secretKeyRef.name }}
          - mountPath: "/certs"
            name: "certs"
            readOnly: true
          {{- end }}
          {{- if $.Values.kafka.sasl.enabled  }}
          - mountPath: "/etc/config"
            name: "jaas-conf"
            readOnly: true
          {{- end }}
          {{- if $.Values.dumpHostPath }}
          - mountPath: /dumps
            name: dumps
            subPathExpr: $(K8S_POD_NAME)
          {{- end }}
          {{ include "corda.log4jVolumeMount" $ | nindent 10 }}
        ports:
        {{- if .debug.enabled }}
          - name: debug
            containerPort: 5005
        {{- end }}
        {{- if $optionalArgs.httpPort }}
          - name: http
            containerPort: {{ $optionalArgs.httpPort }}
        {{- end }}
          - name: monitor
            containerPort: 7000
        {{- if .profiling.enabled }}
          - name: profiling
            containerPort: 10045
        {{- end }}
        {{- if not .debug.enabled }}
        readinessProbe:
          httpGet:
            path: /status
            port: monitor
          periodSeconds: 10
          failureThreshold: 3
          timeoutSeconds: 5
        livenessProbe:
          httpGet:
            path: /isHealthy
            port: monitor
          periodSeconds: 10
          failureThreshold: 3
          timeoutSeconds: 5
        startupProbe:
          httpGet:
            path: /isHealthy
            port: monitor
          periodSeconds: 5
          failureThreshold: 20
          timeoutSeconds: 5
        {{- end }}
      initContainers:
        {{- if $.Values.kafka.sasl.enabled }}
        - name: create-sasl-jaas-conf
          image: {{ include "corda.workerImage" ( list $ . ) }}
          imagePullPolicy:  {{ $.Values.imagePullPolicy }}
          env:
            {{- if $.Values.kafka.sasl.enabled }}
              {{- $username := .kafka.sasl.username }}
            - name: SASL_USERNAME
              {{- if $username.valueFrom.secretKeyRef.name }}
              valueFrom:
                secretKeyRef:
                  name: {{ $username.valueFrom.secretKeyRef.name | quote }}
                  key: {{ required (printf "Must specify %s.kafka.sasl.username.valueFrom.secretKeyRef.key" $worker) $username.valueFrom.secretKeyRef.key | quote }}
              {{- else if $username.value }}
              value: {{ $username.value | quote }}
              {{- else if $.Values.kafka.sasl.username.valueFrom.secretKeyRef.name }}
              valueFrom:
                secretKeyRef:
                  name: {{ $.Values.kafka.sasl.username.valueFrom.secretKeyRef.name | quote }}
                  key: {{ required "Must specify kafka.sasl.username.valueFrom.secretKeyRef.key" $.Values.kafka.sasl.username.valueFrom.secretKeyRef.key | quote }}
              {{- else }}
              value: {{ required (printf "Must specify workers.%s.kafka.sasl.username.value, workers.%s.kafka.sasl.username.valueFrom.secretKeyRef.name, kafka.sasl.username.value, or kafka.sasl.username.valueFrom.secretKeyRef.name" $worker $worker) $.Values.kafka.sasl.username.value }}
              {{- end }}
              {{- $password := .kafka.sasl.password }}
            - name: SASL_PASSWORD
              {{- if $password.valueFrom.secretKeyRef.name }}
              valueFrom:
                secretKeyRef:
                  name: {{ $password.valueFrom.secretKeyRef.name | quote }}
                  key: {{ required (printf "Must specify %s.kafka.sasl.password.valueFrom.secretKeyRef.key" $worker) $password.valueFrom.secretKeyRef.key | quote }}
              {{- else if $password.value }}
              value: {{ $password.value | quote }}
              {{- else if $.Values.kafka.sasl.password.valueFrom.secretKeyRef.name }}
              valueFrom:
                secretKeyRef:
                  name: {{ $.Values.kafka.sasl.password.valueFrom.secretKeyRef.name | quote }}
                  key: {{ required "Must specify kafka.sasl.password.valueFrom.secretKeyRef.key" $.Values.kafka.sasl.password.valueFrom.secretKeyRef.key | quote }}
              {{- else }}
              value: {{ required (printf "Must specify workers.%s.kafka.sasl.password.value, workers.%s.kafka.sasl.password.valueFrom.secretKeyRef.name, kafka.sasl.password.value, or kafka.sasl.password.valueFrom.secretKeyRef.name" $worker $worker) $.Values.kafka.sasl.password.value }}
              {{- end }}
            {{- end }}
          {{- include "corda.containerSecurityContext" $ | nindent 10 }}
          command:
          - /bin/bash
          - -c
          args:
            - |
                cat <<EOF > /etc/config/jaas.conf
                KafkaClient {
                    {{- if eq $.Values.kafka.sasl.mechanism "PLAIN" }}
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
          {{- include "corda.bootstrapResources" $ | nindent 10 }}
        {{- end }}
      volumes:
        {{- if and $.Values.kafka.tls.enabled $.Values.kafka.tls.truststore.valueFrom.secretKeyRef.name }}
        - name: certs
          secret:
            secretName: {{ $.Values.kafka.tls.truststore.valueFrom.secretKeyRef.name | quote }}
            items:
              - key: {{ $.Values.kafka.tls.truststore.valueFrom.secretKeyRef.key | quote }}
                path: "ca.crt"
        {{- end -}}
        {{- if $.Values.kafka.sasl.enabled  }}
        - name: jaas-conf
          emptyDir: {}
        {{- end }}
        {{- if $.Values.dumpHostPath }}
        - name: dumps
          hostPath:
            path: {{ $.Values.dumpHostPath }}/{{ $.Release.Namespace }}/
            type: DirectoryOrCreate
        {{- end }}
        {{ include "corda.log4jVolume" $ | nindent 8 }}
      {{- with $.Values.nodeSelector }}
      nodeSelector:
        {{- toYaml . | nindent 8 }}
      {{- end }}
{{- end }}
{{- end }}

{{/*
Worker type in kebab case
*/}}
{{- define "corda.workerTypeKebabCase" -}}
{{ . | kebabcase | replace "p-2p" "p2p" }}
{{- end }}

{{/*
Worker type in upper snake case
*/}}
{{- define "corda.workerTypeUpperSnakeCase" -}}
{{ . | snakecase | replace "p_2p" "p2p" | upper }}
{{- end }}

{{/*
Worker common labels
*/}}
{{- define "corda.workerLabels" -}}
{{- $ := index . 0 }}
{{- $worker := index . 1 }}
{{ include "corda.labels" $ }}
{{ include "corda.workerComponentLabel" $worker }}
{{- end }}

{{/*
Worker selector labels
*/}}
{{- define "corda.workerSelectorLabels" -}}
{{- $ := index . 0 }}
{{- $worker := index . 1 }}
{{ include "corda.selectorLabels" $ }}
{{ include "corda.workerComponentLabel" $worker }}
{{- end }}

{{/*
Worker component
*/}}
{{- define "corda.workerComponent" -}}
{{ . }}-worker
{{- end }}

{{/*
Worker component label
*/}}
{{- define "corda.workerComponentLabel" -}}
app.kubernetes.io/component: {{ include "corda.workerComponent" . }}
{{- end }}

{{/*
Worker image
*/}}
{{- define "corda.workerImage" -}}
{{- $ := index . 0 }}
{{- with index . 1 }}
{{- printf "%s/%s:%s" ( .image.registry | default $.Values.image.registry ) ( .image.repository ) ( .image.tag | default $.Values.image.tag | default $.Chart.AppVersion ) | quote }}
{{- end }}
{{- end }}
