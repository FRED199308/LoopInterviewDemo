# LoopDFS POS Integration – Kubernetes Troubleshooting Guide

## Quick-Reference Diagnostic Commands

```bash
NS="pos-integration"

# Overview of everything in the namespace
kubectl get all -n $NS

# Events (sorted by time – the single most useful first step)
kubectl get events -n $NS --sort-by='.lastTimestamp'

# Application pod logs
kubectl logs -l app=pos-integration -n $NS --tail=100

# Follow logs live
kubectl logs -l app=pos-integration -n $NS -f

# MySQL pod logs
kubectl logs -l app=mysql -n $NS --tail=100

# Describe a specific pod (shows events, status, resource usage)
kubectl describe pod <pod-name> -n $NS
```

---

## Problem 1 – Pods Are Stuck in `Pending`

**Symptoms:** `kubectl get pods` shows `Pending` indefinitely.

**Diagnosis:**
```bash
kubectl describe pod <pod-name> -n pos-integration
# Look at the "Events" section at the bottom
```

**Common causes and fixes:**

| Cause | Event message | Fix |
|-------|--------------|-----|
| Insufficient CPU/memory | `Insufficient cpu` | Lower `resources.requests` or add nodes |
| No matching nodes | `0/N nodes are available` | Check node selectors/taints |
| PVC unbound | `pod has unbound PersistentVolumeClaims` | Check StorageClass exists: `kubectl get sc` |
| Image pull failure | `Back-off pulling image` | See Problem 3 |

```bash
# Check available node resources
kubectl describe nodes | grep -A 5 "Allocated resources"

# Check PVC status
kubectl get pvc -n pos-integration
kubectl describe pvc mysql-pvc -n pos-integration
```

---

## Problem 2 – Pods Crash-Loop (`CrashLoopBackOff`)

**Symptoms:** Pods restart repeatedly.

**Diagnosis:**
```bash
# See the last crash log (--previous flag)
kubectl logs <pod-name> -n pos-integration --previous

# Check exit code
kubectl describe pod <pod-name> -n pos-integration | grep -A 3 "Last State"
```

**Common causes:**

### A. Cannot connect to MySQL
```
com.mysql.cj.exceptions.CJCommunicationsException: Communications link failure
```
Fix:
```bash
# Check MySQL pod is running
kubectl get pods -l app=mysql -n pos-integration

# Test connectivity from app pod
kubectl exec -it <app-pod> -n pos-integration -- \
  sh -c "nc -zv mysql-service 3306 && echo OK"

# Check the DB_URL ConfigMap value
kubectl get configmap pos-integration-config -n pos-integration -o yaml
```

### B. Wrong DB credentials
```
Access denied for user 'root'@'...'
```
Fix:
```bash
# Verify the secret values (base64 decoded)
kubectl get secret pos-integration-secret -n pos-integration -o jsonpath='{.data.DB_PASSWORD}' | base64 -d
```
Re-apply the secret after correcting values:
```bash
kubectl apply -f k8s/02-secret.yaml
kubectl rollout restart deployment/pos-integration -n pos-integration
```

### C. OOMKilled (Out of Memory)
```
Last State: Terminated  Reason: OOMKilled
```
Fix: Increase memory limit in `k8s/04-deployment.yaml`:
```yaml
resources:
  limits:
    memory: "2Gi"    # increase from 1Gi
```
```bash
kubectl apply -f k8s/04-deployment.yaml
```

### D. Application failed to start (misconfiguration)
```bash
kubectl logs <pod-name> -n pos-integration --previous | grep "APPLICATION FAILED TO START" -A 20
```

---

## Problem 3 – Image Pull Errors (`ImagePullBackOff` / `ErrImagePull`)

**Diagnosis:**
```bash
kubectl describe pod <pod-name> -n pos-integration | grep -A 5 "Events"
```

**Fix:**
```bash
# Verify image name matches what was pushed
kubectl get deployment pos-integration -n pos-integration \
  -o jsonpath='{.spec.template.spec.containers[0].image}'

# For private registry – create imagePullSecret
kubectl create secret docker-registry regcred \
  --docker-server=docker.io \
  --docker-username=<username> \
  --docker-password=<password> \
  -n pos-integration

# Reference the secret in deployment (add under spec.template.spec)
# imagePullSecrets:
#   - name: regcred
kubectl patch deployment pos-integration -n pos-integration \
  -p '{"spec":{"template":{"spec":{"imagePullSecrets":[{"name":"regcred"}]}}}}'
```

---

## Problem 4 – Service Returns 503 / No Endpoints

**Symptoms:** Requests to the service return 503 or time out.

**Diagnosis:**
```bash
# Check if the service has endpoints
kubectl get endpoints pos-integration-service -n pos-integration

# Empty endpoints means no healthy pods match the selector
kubectl get pods -l app=pos-integration -n pos-integration

# Verify selector matches pod labels
kubectl get svc pos-integration-service -n pos-integration -o yaml | grep selector -A 5
kubectl get pods -n pos-integration --show-labels
```

**Fix:** Ensure `app: pos-integration` label exists on pods and matches service selector.

---

## Problem 5 – Readiness Probe Failing

**Symptoms:** Pod is `Running` but `READY 0/1`.

**Diagnosis:**
```bash
kubectl describe pod <pod-name> -n pos-integration | grep -A 10 "Readiness"

# Manually hit the health endpoint from inside the pod
kubectl exec -it <pod-name> -n pos-integration -- \
  wget -qO- http://localhost:8080/actuator/health
```

**Common causes:**
- App still starting (increase `initialDelaySeconds`)
- MySQL not yet ready
- Application threw exception during startup

---

## Problem 6 – SOAP Integration Errors

**Symptoms:** POST `/api/v1/countries` returns 502 Bad Gateway.

**Check application logs:**
```bash
kubectl logs -l app=pos-integration -n pos-integration | grep -i "soap\|integration\|error"
```

**Diagnose network access to external SOAP endpoint:**
```bash
kubectl exec -it <pod-name> -n pos-integration -- \
  wget -qO- "http://webservices.oorsprong.org/websamples.countryinfo/CountryInfoService.wso?WSDL" \
  | head -20
```

If the WSDL is unreachable, check your cluster's egress network policies and DNS:
```bash
kubectl exec -it <pod-name> -n pos-integration -- nslookup webservices.oorsprong.org
```

---

## Problem 7 – HPA Not Scaling

**Diagnosis:**
```bash
kubectl describe hpa pos-integration-hpa -n pos-integration
# Check "Conditions" and "Events" at the bottom
```

**Common fix:** Metrics Server must be installed:
```bash
kubectl top pods -n pos-integration   # if this fails, install metrics-server

# Install metrics-server
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
```

---

## Problem 8 – Database Schema Errors

**Symptoms:** Hibernate errors like `Table 'X' doesn't exist` or column mismatch.

**Fix:**
```bash
# Connect to MySQL pod and inspect
kubectl exec -it <mysql-pod> -n pos-integration -- \
  mysql -u root -p pos_integration_db

# Inside MySQL:
SHOW TABLES;
DESCRIBE country_info;
DESCRIBE languages;
```

Set `spring.jpa.hibernate.ddl-auto=update` (already configured) or run:
```bash
kubectl rollout restart deployment/pos-integration -n pos-integration
```

---

## Useful One-Liners

```bash
NS="pos-integration"

# Stream all pod logs together
kubectl logs -l app=pos-integration -n $NS -f --prefix

# Force-delete a stuck pod
kubectl delete pod <pod-name> -n $NS --force --grace-period=0

# Restart all app pods (triggers rolling update)
kubectl rollout restart deployment/pos-integration -n $NS

# Check resource consumption
kubectl top pods -n $NS
kubectl top nodes

# Get a shell inside an app pod
kubectl exec -it <pod-name> -n $NS -- sh

# Check all config values injected into the pod
kubectl exec -it <pod-name> -n $NS -- env | sort

# Check DNS resolution
kubectl exec -it <pod-name> -n $NS -- nslookup mysql-service

# Copy a log file from a pod to local machine
kubectl cp $NS/<pod-name>:/app/logs/pos-integration.log ./pos-integration.log
```

---

## Health Check Endpoints

| Endpoint | Purpose |
|----------|---------|
| `GET /actuator/health` | Overall health (UP/DOWN) |
| `GET /actuator/health/liveness` | Liveness (is app alive?) |
| `GET /actuator/health/readiness` | Readiness (can it serve traffic?) |
| `GET /actuator/metrics` | All Micrometer metrics |
| `GET /actuator/prometheus` | Prometheus-format metrics scrape |
| `GET /actuator/info` | App name, version, description |

---

## Monitoring Metrics to Watch

| Metric | Alert threshold |
|--------|----------------|
| `jvm_memory_used_bytes` | > 90% of limit |
| `http_server_requests_seconds_max` | > 5 s |
| `hikaricp_connections_active` | > 8 (of 10 pool size) |
| Pod restarts | > 3 in 10 min |
| HPA replicas | At maxReplicas (10) |
