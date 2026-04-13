# LoopDFS POS Integration – Kubernetes Deployment Guide

## Overview

This guide walks you through deploying the POS Integration Spring Boot application on a Kubernetes cluster from scratch. The application integrates with a SOAP-based country information service and persists data in MySQL.

---

## Architecture

```
Internet
   │
   ▼
[ Ingress / Load Balancer ]
   │
   ▼
[ pos-integration Service (ClusterIP) ]
   │
   ├──▶ Pod 1 (Spring Boot)
   └──▶ Pod 2 (Spring Boot)  ← HPA scales up to 10 replicas
           │
           ▼
   [ mysql-service (ClusterIP) ]
           │
           ▼
   [ MySQL Pod + PersistentVolumeClaim ]
```

**Design decisions:**
- Spring Boot pods are **stateless** – all state lives in MySQL, enabling safe horizontal scaling.
- An **initContainer** prevents the app from starting before MySQL is ready.
- A **RollingUpdate** strategy with `maxUnavailable: 0` gives zero-downtime deploys.
- **HPA** scales on CPU (70%) and memory (80%) between 2–10 replicas.
- Credentials are stored in **Secrets**, not ConfigMaps.

---

## Prerequisites

| Tool | Minimum Version | Install |
|------|----------------|---------|
| `kubectl` | 1.26+ | https://kubernetes.io/docs/tasks/tools/ |
| `docker` | 24+ | https://docs.docker.com/get-docker/ |
| `mvn` (Maven) | 3.8+ | https://maven.apache.org/download.cgi |
| Kubernetes cluster | 1.26+ | minikube / kind / EKS / GKE / AKS |

Verify your cluster connection:
```bash
kubectl cluster-info
kubectl get nodes
```

---

## Step 1 – Prepare Secrets

The `k8s/02-secret.yaml` file ships with placeholder base64 values.  
**Replace them before deploying:**

```bash
# Encode your actual values
echo -n 'your_db_password' | base64     # e.g. eW91cl9kYl9wYXNzd29yZA==
echo -n 'your_db_username' | base64
echo -n 'mysql_root_password' | base64
```

Edit `k8s/02-secret.yaml` and paste the encoded values, then:

```bash
# Apply the secret (do NOT commit this file to Git with real values)
kubectl apply -f k8s/02-secret.yaml
```

> **Best practice:** Use a secrets manager (AWS Secrets Manager, HashiCorp Vault, Sealed Secrets) in production instead of plaintext YAML.

---

## Step 2 – Build the Docker Image

```bash
# From the project root
mvn clean package -DskipTests

docker build -t LoopDFS/pos-integration:1.0.0 .

# Tag as latest too
docker tag LoopDFS/pos-integration:1.0.0 LoopDFS/pos-integration:latest
```

---

## Step 3 – Push the Image

```bash
# Log into your registry
docker login docker.io   # or your private registry

docker push LoopDFS/pos-integration:1.0.0
docker push LoopDFS/pos-integration:latest
```

If you are using **minikube** (local cluster), skip the push and load the image directly:
```bash
minikube image load LoopDFS/pos-integration:1.0.0
```

If you are using **kind**:
```bash
kind load docker-image LoopDFS/pos-integration:1.0.0
```

---

## Step 4 – Update the Deployment Image Reference

Open `k8s/04-deployment.yaml` and update the image field to match your registry:
```yaml
image: your-registry/pos-integration:1.0.0
```

---

## Step 5 – Apply All Manifests

Apply in order (or use the deploy script):

```bash
kubectl apply -f k8s/00-namespace.yaml
kubectl apply -f k8s/01-configmap.yaml
kubectl apply -f k8s/02-secret.yaml
kubectl apply -f k8s/03-mysql.yaml
kubectl apply -f k8s/04-deployment.yaml
kubectl apply -f k8s/05-service-hpa.yaml
kubectl apply -f k8s/06-ingress.yaml
```

Or use the automated script:
```bash
chmod +x deploy.sh

# With registry push
./deploy.sh

# Local cluster (minikube/kind) – skip push
SKIP_PUSH=true ./deploy.sh
```

---

## Step 6 – Verify the Deployment

```bash
# Check all resources in the namespace
kubectl get all -n pos-integration

# Expected output (example):
# NAME                                   READY   STATUS    RESTARTS
# pod/mysql-xxxxxxx                      1/1     Running   0
# pod/pos-integration-xxxxxxx-1          1/1     Running   0
# pod/pos-integration-xxxxxxx-2          1/1     Running   0
#
# NAME                            TYPE        CLUSTER-IP
# service/mysql-service           ClusterIP   10.96.x.x
# service/pos-integration-service ClusterIP   10.96.x.x
#
# NAME                              READY   UP-TO-DATE   AVAILABLE
# deployment.apps/pos-integration   2/2     2            2
# deployment.apps/mysql             1/1     1            1
#
# NAME                                           REFERENCE              MINPODS   MAXPODS
# horizontalpodautoscaler.autoscaling/pos-hpa    Deployment/pos-integration 2      10

# Watch rollout progress
kubectl rollout status deployment/pos-integration -n pos-integration
```

---

## Step 7 – Test the Application

```bash
# Port-forward to test locally
kubectl port-forward svc/pos-integration-service 8080:80 -n pos-integration

# In another terminal:

# Health check
curl http://localhost:8080/actuator/health

# Fetch and save a country
curl -X POST http://localhost:8080/api/v1/countries \
  -H "Content-Type: application/json" \
  -d '{"name": "Kenya"}'

# List all countries
curl http://localhost:8080/api/v1/countries

# Get by ID
curl http://localhost:8080/api/v1/countries/1

# Update
curl -X PUT http://localhost:8080/api/v1/countries/1 \
  -H "Content-Type: application/json" \
  -d '{"capitalCity": "Nairobi"}'

# Delete
curl -X DELETE http://localhost:8080/api/v1/countries/1
```

---

## Step 8 – Configure Ingress (Production)

Ensure the NGINX Ingress Controller is installed:
```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/cloud/deploy.yaml
```

Update the host in `k8s/06-ingress.yaml` to your domain, then apply:
```bash
kubectl apply -f k8s/06-ingress.yaml
kubectl get ingress -n pos-integration
```

---

## Updating the Application

```bash
# Build new image
docker build -t LoopDFS/pos-integration:1.0.1 .
docker push LoopDFS/pos-integration:1.0.1

# Rolling update (zero downtime)
kubectl set image deployment/pos-integration \
  pos-integration=LoopDFS/pos-integration:1.0.1 \
  -n pos-integration

# Monitor rollout
kubectl rollout status deployment/pos-integration -n pos-integration
```

## Rolling Back

```bash
# Immediate rollback to previous version
kubectl rollout undo deployment/pos-integration -n pos-integration

# Roll back to a specific revision
kubectl rollout history deployment/pos-integration -n pos-integration
kubectl rollout undo deployment/pos-integration --to-revision=2 -n pos-integration
```

---

## Scaling

```bash
# Manual scale
kubectl scale deployment/pos-integration --replicas=5 -n pos-integration

# HPA handles auto-scaling – check current state
kubectl get hpa -n pos-integration
kubectl describe hpa pos-integration-hpa -n pos-integration
```
