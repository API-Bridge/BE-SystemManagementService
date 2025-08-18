#!/bin/bash

# System Management Service Deployment Script
set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
DOCKER_REGISTRY="${DOCKER_REGISTRY:-your-docker-registry.com}"
IMAGE_NAME="${IMAGE_NAME:-system-management-service}"
ENVIRONMENT="${ENVIRONMENT:-staging}"
NAMESPACE="${NAMESPACE:-system-management-${ENVIRONMENT}}"
ARGOCD_SERVER="${ARGOCD_SERVER:-argocd.your-company.com}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."
    
    local missing_tools=()
    
    command -v docker >/dev/null 2>&1 || missing_tools+=("docker")
    command -v kubectl >/dev/null 2>&1 || missing_tools+=("kubectl")
    command -v argocd >/dev/null 2>&1 || missing_tools+=("argocd")
    command -v kustomize >/dev/null 2>&1 || missing_tools+=("kustomize")
    
    if [ ${#missing_tools[@]} -ne 0 ]; then
        log_error "Missing required tools: ${missing_tools[*]}"
        log_error "Please install the missing tools and try again."
        exit 1
    fi
    
    log_success "All prerequisites met"
}

# Function to build and push Docker image
build_and_push_image() {
    local tag="${1:-latest}"
    local full_tag="${DOCKER_REGISTRY}/${IMAGE_NAME}:${tag}"
    
    log_info "Building Docker image: ${full_tag}"
    
    cd "$PROJECT_ROOT"
    
    # Build the image
    docker build -t "$full_tag" .
    
    # Security scan (optional)
    if command -v trivy >/dev/null 2>&1; then
        log_info "Running security scan..."
        trivy image --exit-code 1 --severity HIGH,CRITICAL "$full_tag" || {
            log_warning "Security scan found issues, but continuing..."
        }
    fi
    
    # Push to registry
    log_info "Pushing image to registry..."
    docker push "$full_tag"
    
    log_success "Image built and pushed: ${full_tag}"
    echo "$full_tag"
}

# Function to deploy to Kubernetes using ArgoCD
deploy_with_argocd() {
    local image_tag="$1"
    local app_name="system-management-service-${ENVIRONMENT}"
    
    log_info "Deploying ${app_name} with image tag: ${image_tag}"
    
    # Login to ArgoCD (assuming token is set)
    if [ -z "${ARGOCD_AUTH_TOKEN:-}" ]; then
        log_error "ARGOCD_AUTH_TOKEN environment variable is not set"
        log_info "Please run: export ARGOCD_AUTH_TOKEN=<your-token>"
        exit 1
    fi
    
    # Update image tag in the git repository
    # This assumes you have a separate k8s-manifests repository
    if [ -n "${K8S_MANIFESTS_REPO:-}" ]; then
        update_k8s_manifests "$image_tag"
    fi
    
    # Sync the application
    log_info "Syncing ArgoCD application: ${app_name}"
    argocd app sync "$app_name" --server "$ARGOCD_SERVER" --auth-token "$ARGOCD_AUTH_TOKEN"
    
    # Wait for sync to complete
    log_info "Waiting for deployment to complete..."
    argocd app wait "$app_name" --server "$ARGOCD_SERVER" --auth-token "$ARGOCD_AUTH_TOKEN" --timeout 600
    
    log_success "Deployment completed successfully"
}

# Function to update Kubernetes manifests
update_k8s_manifests() {
    local image_tag="$1"
    local manifests_dir="${K8S_MANIFESTS_REPO:-../k8s-manifests}"
    local overlay_path="${manifests_dir}/system-management-service/overlays/${ENVIRONMENT}"
    
    if [ ! -d "$overlay_path" ]; then
        log_error "Overlay path not found: ${overlay_path}"
        return 1
    fi
    
    log_info "Updating Kubernetes manifests with new image tag..."
    
    cd "$manifests_dir"
    
    # Update kustomization.yaml with new image tag
    if [ -f "${overlay_path}/kustomization.yaml" ]; then
        sed -i.bak "s|newTag:.*|newTag: ${image_tag}|g" "${overlay_path}/kustomization.yaml"
        rm -f "${overlay_path}/kustomization.yaml.bak"
    fi
    
    # Commit and push changes
    git add .
    git commit -m "Update system-management-service image to ${image_tag} for ${ENVIRONMENT}"
    git push origin main
    
    log_success "Kubernetes manifests updated"
}

# Function to deploy directly with kubectl (fallback)
deploy_with_kubectl() {
    local image_tag="$1"
    local manifests_path="${PROJECT_ROOT}/k8s/overlays/${ENVIRONMENT}"
    
    log_info "Deploying directly with kubectl..."
    
    # Check if namespace exists
    if ! kubectl get namespace "$NAMESPACE" >/dev/null 2>&1; then
        log_info "Creating namespace: ${NAMESPACE}"
        kubectl create namespace "$NAMESPACE"
    fi
    
    # Update image tag in kustomization
    cd "$manifests_path"
    kustomize edit set image "${DOCKER_REGISTRY}/${IMAGE_NAME}=${DOCKER_REGISTRY}/${IMAGE_NAME}:${image_tag}"
    
    # Apply manifests
    log_info "Applying Kubernetes manifests..."
    kustomize build . | kubectl apply -f -
    
    # Wait for deployment
    log_info "Waiting for deployment to be ready..."
    kubectl rollout status deployment/system-management-service -n "$NAMESPACE" --timeout=600s
    
    log_success "Direct deployment completed"
}

# Function to run health checks
health_check() {
    local max_attempts=30
    local attempt=1
    
    log_info "Running health checks..."
    
    while [ $attempt -le $max_attempts ]; do
        if kubectl get pods -n "$NAMESPACE" -l app=system-management-service --field-selector=status.phase=Running | grep -q "Running"; then
            local pod_name=$(kubectl get pods -n "$NAMESPACE" -l app=system-management-service --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}')
            
            if kubectl exec -n "$NAMESPACE" "$pod_name" -- curl -f http://localhost:8080/api/v1/health >/dev/null 2>&1; then
                log_success "Health check passed"
                return 0
            fi
        fi
        
        log_info "Health check attempt ${attempt}/${max_attempts} failed, retrying in 10s..."
        sleep 10
        ((attempt++))
    done
    
    log_error "Health check failed after ${max_attempts} attempts"
    return 1
}

# Function to show deployment status
show_status() {
    log_info "Deployment Status:"
    echo "===================="
    
    kubectl get pods -n "$NAMESPACE" -l app=system-management-service
    echo
    kubectl get svc -n "$NAMESPACE" -l app=system-management-service
    echo
    kubectl get hpa -n "$NAMESPACE" -l app=system-management-service 2>/dev/null || true
}

# Function to rollback deployment
rollback() {
    log_warning "Rolling back deployment..."
    kubectl rollout undo deployment/system-management-service -n "$NAMESPACE"
    kubectl rollout status deployment/system-management-service -n "$NAMESPACE"
    log_success "Rollback completed"
}

# Main deployment function
main() {
    local command="${1:-deploy}"
    local tag="${2:-$(date +%Y%m%d-%H%M%S)-$(git rev-parse --short HEAD)}"
    
    case "$command" in
        "build")
            check_prerequisites
            build_and_push_image "$tag"
            ;;
        "deploy")
            check_prerequisites
            local image_tag
            image_tag=$(build_and_push_image "$tag")
            
            if command -v argocd >/dev/null 2>&1 && [ -n "${ARGOCD_AUTH_TOKEN:-}" ]; then
                deploy_with_argocd "$image_tag"
            else
                log_warning "ArgoCD not available, falling back to direct kubectl deployment"
                deploy_with_kubectl "$image_tag"
            fi
            
            health_check
            show_status
            ;;
        "status")
            show_status
            ;;
        "rollback")
            rollback
            ;;
        "health")
            health_check
            ;;
        *)
            echo "Usage: $0 {build|deploy|status|rollback|health} [tag]"
            echo ""
            echo "Commands:"
            echo "  build     Build and push Docker image"
            echo "  deploy    Build, push, and deploy to Kubernetes"
            echo "  status    Show deployment status"
            echo "  rollback  Rollback to previous deployment"
            echo "  health    Run health checks"
            echo ""
            echo "Environment Variables:"
            echo "  ENVIRONMENT           Target environment (staging|production)"
            echo "  DOCKER_REGISTRY       Docker registry URL"
            echo "  ARGOCD_AUTH_TOKEN     ArgoCD authentication token"
            echo "  K8S_MANIFESTS_REPO    Path to k8s-manifests repository"
            exit 1
            ;;
    esac
}

# Execute main function with all arguments
main "$@"