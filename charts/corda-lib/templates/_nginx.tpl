{{- define "corda.nginxName" -}}
{{- printf "%s-nginx" . }}
{{- end }}

{{- define "corda.nginxComponent" -}}
{{ printf "%s-nginx" . }}
{{- end }}

{{- define "corda.nginxLabels" -}}
{{- $workerName := index . 1 }}
{{- with ( index . 0 ) }}
{{- include "corda.labels" . }}
app.kubernetes.io/component: {{ include "corda.nginxComponent" $workerName }}
{{- end }}
{{- end }}

{{- define "corda.nginxSelectorLabels" -}}
{{- $workerName := index . 1 }}
{{- with ( index . 0 ) }}
{{- include "corda.selectorLabels" . }}
app.kubernetes.io/component: {{ include "corda.nginxComponent" $workerName }}
{{- end }}
{{- end }}

{{- define "corda.nginx" }}
{{- $workerName := index . 1 }}
{{- $shardingConfig := index . 2 }}
{{- with ( index . 0 ) }}
---
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  labels:
    {{- include "corda.nginxLabels" ( list . $workerName ) | nindent 4 }}
  name: {{ include "corda.nginxName" $workerName | quote }}
spec:
  selector:
    matchLabels:
      {{ include "corda.nginxSelectorLabels" ( list . $workerName ) | nindent 6 }}
  minAvailable: 1
---
apiVersion: v1
kind: ServiceAccount
metadata:
  labels:
    {{- include "corda.nginxLabels" ( list . $workerName ) | nindent 4 }}
  name: {{ include "corda.nginxName" $workerName | quote }}
---
apiVersion: v1
kind: ConfigMap
metadata:
  labels:
    {{- include "corda.nginxLabels" ( list . $workerName ) | nindent 4 }}
  name: {{ include "corda.nginxName" $workerName | quote }}
data:
  allow-snippet-annotations: "false"
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  labels:
    {{- include "corda.nginxLabels" ( list . $workerName ) | nindent 4 }}
  name: {{ include "corda.nginxName" $workerName | quote }}
rules:
  - apiGroups:
      - ""
    resources:
      - namespaces
    verbs:
      - get
  - apiGroups:
      - ""
    resources:
      - configmaps
      - pods
      - secrets
      - endpoints
    verbs:
      - get
      - list
      - watch
  - apiGroups:
      - ""
    resources:
      - services
    verbs:
      - get
      - list
      - watch
  - apiGroups:
      - networking.k8s.io
    resources:
      - ingresses
    verbs:
      - get
      - list
      - watch
  - apiGroups:
      - networking.k8s.io
    resources:
      - ingresses/status
    verbs:
      - update
  - apiGroups:
      - networking.k8s.io
    resources:
      - ingressclasses
    verbs:
      - get
      - list
      - watch
  - apiGroups:
      - coordination.k8s.io
    resources:
      - leases
    resourceNames:
      - {{ include "corda.nginxName" $workerName }}-leader
    verbs:
      - get
      - update
  - apiGroups:
      - coordination.k8s.io
    resources:
      - leases
    verbs:
      - create
  - apiGroups:
      - ""
    resources:
      - events
    verbs:
      - create
      - patch
  - apiGroups:
      - discovery.k8s.io
    resources:
      - endpointslices
    verbs:
      - list
      - watch
      - get
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  labels:
    {{- include "corda.nginxLabels" ( list . $workerName ) | nindent 4 }}
  name: {{ include "corda.nginxName" $workerName | quote }}
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: {{ include "corda.nginxName" $workerName | quote }}
subjects:
  - kind: ServiceAccount
    name: {{ include "corda.nginxName" $workerName | quote }}
    namespace: {{ .Release.Namespace | quote }}
---
apiVersion: v1
kind: Service
metadata:
  labels:
    {{- include "corda.nginxLabels" ( list . $workerName ) | nindent 4 }}
  name: {{ include "corda.nginxName" $workerName | quote }}
spec:
  type: ClusterIP
  ipFamilyPolicy: SingleStack
  ipFamilies:
    - IPv4
  ports:
    - name: http
      port: {{ include "corda.workerServicePort" ( list . $workerName ) }}
      protocol: TCP
      targetPort: http
      appProtocol: http
  selector:
    {{ include "corda.nginxSelectorLabels" ( list . $workerName ) | nindent 4 }}
---
apiVersion: apps/v1
kind: Deployment
metadata:
  labels:
    {{- include "corda.nginxLabels" ( list . $workerName ) | nindent 4 }}
  name: {{ include "corda.nginxName" $workerName | quote }}
spec:
  selector:
    matchLabels:
      {{ include "corda.nginxSelectorLabels" ( list . $workerName ) | nindent 6 }}
  replicas: {{ $shardingConfig.replicaCount }}
  revisionHistoryLimit: 10
  minReadySeconds: 0
  template:
    metadata:
      labels:
        {{- include "corda.nginxLabels" ( list . $workerName ) | nindent 8 }}
      {{- with .Values.annotations }}
      annotations:
        {{- toYaml . | nindent 8 }}
      {{- end }}
    spec:
      dnsPolicy: ClusterFirst
      {{- with .Values.podSecurityContext }}
      securityContext:
        {{- . | toYaml | nindent 8 }}
      {{- end }}
      {{- include "corda.imagePullSecrets" . | indent 6 }}
      {{- include "corda.tolerations" . | indent 6 }}
      {{- with .Values.serviceAccount.name  }}
      serviceAccountName: {{ . }}
      {{- end }}
      {{- include "corda.topologySpreadConstraints" . | indent 6 }}
      {{- include "corda.affinity" (list . ( include "corda.nginxComponent" $workerName ) ) | indent 6 }}
      containers:
        - name: controller
          {{- with $shardingConfig.image }}
          image: {{ ( printf "%s/%s:%s" .registry .repository .tag ) | quote }}
          {{- end }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          securityContext:
          {{- with .Values.containerSecurityContext -}}
            {{- . | toYaml | nindent 12}}
          {{- end }}
            capabilities:
              add:
              - NET_BIND_SERVICE
            readOnlyRootFilesystem: false
            runAsUser: 101
          lifecycle:
            preStop:
              exec:
                command:
                - /wait-shutdown
          args:
            - /nginx-ingress-controller
            - --publish-service=$(POD_NAMESPACE)/{{ include "corda.nginxName" $workerName }}
            - --election-id={{ include "corda.nginxName" $workerName }}-leader
            - --controller-class=k8s.io/{{ include "corda.nginxName" $workerName }}
            - --ingress-class={{ include "corda.nginxName" $workerName }}
            - --configmap=$(POD_NAMESPACE)/{{ include "corda.nginxName" $workerName }}
            - --watch-namespace=$(POD_NAMESPACE)
          env:
            - name: POD_NAME
              valueFrom:
                fieldRef:
                  fieldPath: metadata.name
            - name: POD_NAMESPACE
              valueFrom:
                fieldRef:
                  fieldPath: metadata.namespace
            - name: LD_PRELOAD
              value: /usr/local/lib/libmimalloc.so
          livenessProbe:
            failureThreshold: 5
            httpGet:
              path: /healthz
              port: 10254
              scheme: HTTP
            initialDelaySeconds: 10
            periodSeconds: 10
            successThreshold: 1
            timeoutSeconds: 1
          readinessProbe:
            failureThreshold: 3
            httpGet:
              path: /healthz
              port: 10254
              scheme: HTTP
            initialDelaySeconds: 10
            periodSeconds: 10
            successThreshold: 1
            timeoutSeconds: 1
          ports:
            - name: http
              containerPort: 80
              protocol: TCP
          resources:
            requests:
              cpu: 100m
              memory: 90Mi
      {{- with .Values.nodeSelector }}
      nodeSelector:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      serviceAccountName: {{ include "corda.nginxName" $workerName | quote }}
      terminationGracePeriodSeconds: 300
{{- end }}
{{- end }}
