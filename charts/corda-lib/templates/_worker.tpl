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
{{- with .ingress }}
{{- if gt (len .hosts) 0 }}
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: {{ $workerName | quote }}
  labels:
    {{- include "corda.workerLabels" ( list $ $worker ) | nindent 4 }}
  {{- with .annotations }}
  annotations:
    {{- toYaml . | nindent 4 }}
  {{- end }}
spec:
  {{- with .className }}
  ingressClassName: {{ . | quote }}
  {{- end }}
  tls:
  {{- range .hosts }}
    - hosts:
        - {{ . | quote }}
      secretName: {{ . | quote }}
  {{- end }}
  rules:
  {{- range .hosts }}
    - host: {{ . | quote }}
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: {{ $workerName | quote }}
                port:
                  name: http
  {{- end }}
{{- end }}
{{- end }}
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
  {{- end }}
spec:
  {{- with .type }}
  type: {{ . }}
  {{- end }}
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
      {{- with $.Values.podSecurityContext }}
      securityContext:
        {{- . | toYaml | nindent 8 }}
      {{- end }}
      {{- end }}
      {{- include "corda.imagePullSecrets" $ | indent 6 }}
      {{- include "corda.tolerations" $ | indent 6 }}
      {{- with $.Values.serviceAccount.name  }}
      serviceAccountName: {{ . }}
      {{- end }}
      {{- include "corda.topologySpreadConstraints" $ | indent 6 }}
      {{- include "corda.affinity" (list $ . $worker ) | indent 6 }}
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
          - name: ENABLE_CLOUDWATCH
            value:
              {{- if eq $.Values.serviceAccount.name "cloudwatch-writer" }}
                "true"
              {{- else }}
                "false"
              {{- end }}
          - name: JAVA_TOOL_OPTIONS
            value:
              {{ .javaOptions }}
              {{- if .debug.enabled }}
                -agentlib:jdwp=transport=dt_socket,server=y,address=5005,suspend={{ if .debug.suspend }}y{{ else }}n{{ end }}
              {{- end -}}
              {{- if .profiling.enabled }}
                -agentpath:/opt/override/libyjpagent.so=exceptions=disable,port=10045,listen=all,dir=/dumps/profile/snapshots,logdir=/dumps/profile/logs
              {{- end -}}
              {{- if $.Values.heapDumpOnOutOfMemoryError }}
                -XX:+HeapDumpOnOutOfMemoryError
                -XX:HeapDumpPath=/dumps/heap
              {{- end -}}
              {{- if .verifyInstrumentation }}
                -Dco.paralleluniverse.fibers.verifyInstrumentation=true
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
        {{- if not (($.Values).vault).url }}
        {{- include "corda.configSaltAndPassphraseEnv" $ | nindent 10 }}
        {{- end }}
        {{- /* TODO-[CORE-16419]: isolate StateManager database from the Cluster database */ -}}
        {{- if or $optionalArgs.clusterDbAccess $optionalArgs.stateManagerDbAccess }}
        {{- include "corda.clusterDbEnv" $ | nindent 10 }}
        {{- end }}
        args:
          - "--workspace-dir=/work"
          - "--temp-dir=/tmp"
          - "-mbootstrap.servers={{ include "corda.kafkaBootstrapServers" $ }}"
          {{- if $.Values.kafka.sasl.enabled }}
          - "-msasl.jaas.config=org.apache.kafka.common.security.{{- if eq $.Values.kafka.sasl.mechanism "PLAIN" }}plain.PlainLoginModule{{- else }}scram.ScramLoginModule{{- end }} required username=\"$(SASL_USERNAME)\" password=\"$(SASL_PASSWORD)\";"
          {{- end }}
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
          {{- if .clusterDbConnectionPool.minSize }}
          - "-ddatabase.pool.min_size={{ .clusterDbConnectionPool.minSize }}"
          {{- end }}
          - "-ddatabase.pool.idleTimeoutSeconds={{ .clusterDbConnectionPool.idleTimeoutSeconds }}"
          - "-ddatabase.pool.maxLifetimeSeconds={{ .clusterDbConnectionPool.maxLifetimeSeconds }}"
          - "-ddatabase.pool.keepaliveTimeSeconds={{ .clusterDbConnectionPool.keepaliveTimeSeconds }}"
          - "-ddatabase.pool.validationTimeoutSeconds={{ .clusterDbConnectionPool.validationTimeoutSeconds }}"
          {{- end }}
          {{- /* TODO-[CORE-16419]: isolate StateManager database from the Cluster database */ -}}
          {{- if $optionalArgs.stateManagerDbAccess }}
          - "--stateManager"
          - "type=DATABASE"
          - "--stateManager"
          - "database.user=$(DB_CLUSTER_USERNAME)"
          - "--stateManager"
          - "database.pass=$(DB_CLUSTER_PASSWORD)"
          - "--stateManager"
          - "database.jdbc.url=jdbc:postgresql://{{ required "Must specify db.cluster.host" $.Values.db.cluster.host }}:{{ $.Values.db.cluster.port }}/{{ $.Values.db.cluster.database }}?currentSchema={{ $.Values.bootstrap.db.stateManager.schema }}"
          - "--stateManager"
          - "database.jdbc.directory=/opt/jdbc-driver"
          - "--stateManager"
          - "database.jdbc.driver=org.postgresql.Driver"
          - "--stateManager"
          - "database.jdbc.persistenceUnitName=corda-state-manager"
          - "--stateManager"
          - "database.pool.maxSize={{ .stateManagerDbConnectionPool.maxSize }}"
          {{- if .stateManagerDbConnectionPool.minSize }}
          - "--stateManager"
          - "database.pool.minSize={{ .stateManagerDbConnectionPool.minSize }}"
          {{- end }}
          - "--stateManager"
          - "database.pool.idleTimeoutSeconds={{ .stateManagerDbConnectionPool.idleTimeoutSeconds }}"
          - "--stateManager"
          - "database.pool.maxLifetimeSeconds={{ .stateManagerDbConnectionPool.maxLifetimeSeconds }}"
          - "--stateManager"
          - "database.pool.keepAliveTimeSeconds={{ .stateManagerDbConnectionPool.keepAliveTimeSeconds }}"
          - "--stateManager"
          - "database.pool.validationTimeoutSeconds={{ .stateManagerDbConnectionPool.validationTimeoutSeconds }}"
          {{- end }}
          {{- if $.Values.tracing.endpoint }}
          - "--send-trace-to={{ $.Values.tracing.endpoint }}"
          {{- end }}
          {{- if $.Values.tracing.samplesPerSecond }}
          - "--trace-samples-per-second={{ $.Values.tracing.samplesPerSecond }}"
          {{- end }}
          {{- range $i, $arg := $optionalArgs.additionalWorkerArgs }}
          - {{ $arg | quote }}
          {{- end }}
        volumeMounts:
          - mountPath: "/tmp"
            name: "tmp"
            readOnly: false
          - mountPath: "/work"
            name: "work"
            readOnly: false
          {{- if and $.Values.kafka.tls.enabled $.Values.kafka.tls.truststore.valueFrom.secretKeyRef.name }}
          - mountPath: "/certs"
            name: "certs"
            readOnly: true
          {{- end }}
          {{- if .tls }}
          - mountPath: "/tls"
            name: "tlsmount"
            readOnly: true
          {{- end }}
          {{- if $.Values.dumpHostPath }}
          - mountPath: /dumps
            name: dumps
            subPathExpr: $(K8S_POD_NAME)
            readOnly: false
          {{- end }}
          {{- include "corda.log4jVolumeMount" $ | nindent 10 }}
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
      volumes:
        - name: tmp
          emptyDir: {}
        - name: work
          emptyDir: {}
        {{- if and $.Values.kafka.tls.enabled $.Values.kafka.tls.truststore.valueFrom.secretKeyRef.name }}
        - name: certs
          secret:
            secretName: {{ $.Values.kafka.tls.truststore.valueFrom.secretKeyRef.name | quote }}
            items:
              - key: {{ $.Values.kafka.tls.truststore.valueFrom.secretKeyRef.key | quote }}
                path: "ca.crt"
        {{- end -}}
        {{- if .tls }}
        - name: tlsmount
          secret:
            secretName: {{ $optionalArgs.tlsSecretName | quote }}
            items:
              - key: {{ .tls.crt.secretKey | quote }}
                path: "tls.crt"
              - key: {{ .tls.key.secretKey | quote }}
                path: "tls.key"
              - key: {{ .tls.ca.secretKey | quote }}
                path: "ca.crt"
        {{- end -}}
        {{- if $.Values.dumpHostPath }}
        - name: dumps
          hostPath:
            path: {{ $.Values.dumpHostPath }}/{{ $.Release.Namespace }}/
            type: DirectoryOrCreate
        {{- end }}
        {{- include "corda.log4jVolume" $ | nindent 8 }}
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
{{- $ := index . 0 -}}
{{- $worker := index . 1 -}}
{{ include "corda.labels" $ }}
{{ include "corda.workerComponentLabel" $worker }}
{{- end }}

{{/*
Worker selector labels
*/}}
{{- define "corda.workerSelectorLabels" -}}
{{- $ := index . 0 -}}
{{- $worker := index . 1 -}}
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

{{/*
Worker default affinity
*/}}
{{- define "corda.defaultAffinity" -}}
{{- $weight := index . 0 }}
{{- $worker := index . 1 }}
weight: {{ $weight}}
podAffinityTerm:
  labelSelector:
    matchExpressions:
      - key: "app.kubernetes.io/component"
        operator: In
        values:
          - {{ include "corda.workerComponent" $worker }}
  topologyKey: "kubernetes.io/hostname"
{{- end }}

{{/*
Worker affinity
*/}}
{{- define "corda.affinity" -}}
{{- $ := index . 0 }}
{{- $worker := index . 2 }}
{{- $affinity := default ( deepCopy $.Values.affinity ) dict }}
{{- if not ($affinity.podAntiAffinity) }}
{{- $_ := set $affinity "podAntiAffinity" dict }}
{{- end }}
{{- if not ($affinity.podAntiAffinity.preferredDuringSchedulingIgnoredDuringExecution) }}
{{- $_ := set $affinity.podAntiAffinity "preferredDuringSchedulingIgnoredDuringExecution" list }}
{{- end }}
{{- $_ := set $affinity.podAntiAffinity "preferredDuringSchedulingIgnoredDuringExecution" ( append $affinity.podAntiAffinity.preferredDuringSchedulingIgnoredDuringExecution ( fromYaml ( include "corda.defaultAffinity" ( list ( add ( len $affinity.podAntiAffinity.preferredDuringSchedulingIgnoredDuringExecution ) 1 ) $worker ) ) ) ) }}
affinity:
{{- toYaml $affinity | nindent 2 }}
{{- end }}