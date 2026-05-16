# ============================================================================
# WorkHub — Terraform Variables (Kubernetes Track)
# ============================================================================

# ---------------------------------------------------------------------------
# Kubernetes Provider
# ---------------------------------------------------------------------------
variable "kubeconfig_path" {
  description = "Path to the kubeconfig file"
  type        = string
  default     = "~/.kube/config"
}

variable "kubeconfig_context" {
  description = "Kubernetes context to use (e.g. minikube, docker-desktop)"
  type        = string
  default     = "minikube"
}

# ---------------------------------------------------------------------------
# Namespace & Environment
# ---------------------------------------------------------------------------
variable "namespace" {
  description = "Kubernetes namespace for WorkHub resources"
  type        = string
  default     = "workhub"
}

variable "spring_profile" {
  description = "Spring Boot active profile"
  type        = string
  default     = "prod"
}

# ---------------------------------------------------------------------------
# Application Settings
# ---------------------------------------------------------------------------
variable "app_image" {
  description = "Docker image for the WorkHub application"
  type        = string
  default     = "workhub-app:latest"
}

variable "image_pull_policy" {
  description = "Kubernetes image pull policy (IfNotPresent for local builds)"
  type        = string
  default     = "IfNotPresent"
}

variable "app_replicas" {
  description = "Number of WorkHub application replicas"
  type        = number
  default     = 1
}

variable "app_port" {
  description = "Application container port"
  type        = number
  default     = 8080
}

variable "service_type" {
  description = "Kubernetes service type for the app (ClusterIP, NodePort, LoadBalancer)"
  type        = string
  default     = "ClusterIP"
}

# ---------------------------------------------------------------------------
# PostgreSQL Configuration
# ---------------------------------------------------------------------------
variable "postgres_image" {
  description = "Docker image for PostgreSQL"
  type        = string
  default     = "postgres:15"
}

variable "db_name" {
  description = "PostgreSQL database name"
  type        = string
  default     = "workhub"
}

variable "db_username" {
  description = "PostgreSQL username"
  type        = string
  sensitive   = true
}

variable "db_password" {
  description = "PostgreSQL password"
  type        = string
  sensitive   = true
}

# ---------------------------------------------------------------------------
# RabbitMQ Configuration
# ---------------------------------------------------------------------------
variable "rabbitmq_image" {
  description = "Docker image for RabbitMQ"
  type        = string
  default     = "rabbitmq:3.13-management"
}

variable "rabbitmq_username" {
  description = "RabbitMQ default username"
  type        = string
  sensitive   = true
}

variable "rabbitmq_password" {
  description = "RabbitMQ default password"
  type        = string
  sensitive   = true
}

# ---------------------------------------------------------------------------
# JWT Configuration
# ---------------------------------------------------------------------------
variable "jwt_secret" {
  description = "JWT signing secret (min 256-bit)"
  type        = string
  sensitive   = true
}

# ---------------------------------------------------------------------------
# Resource Limits — Application
# ---------------------------------------------------------------------------
variable "app_memory_request" {
  description = "Memory request for the app container"
  type        = string
  default     = "256Mi"
}

variable "app_memory_limit" {
  description = "Memory limit for the app container"
  type        = string
  default     = "512Mi"
}

variable "app_cpu_request" {
  description = "CPU request for the app container"
  type        = string
  default     = "250m"
}

variable "app_cpu_limit" {
  description = "CPU limit for the app container"
  type        = string
  default     = "500m"
}

# ---------------------------------------------------------------------------
# Resource Limits — PostgreSQL
# ---------------------------------------------------------------------------
variable "postgres_memory_request" {
  description = "Memory request for the PostgreSQL container"
  type        = string
  default     = "256Mi"
}

variable "postgres_memory_limit" {
  description = "Memory limit for the PostgreSQL container"
  type        = string
  default     = "512Mi"
}

variable "postgres_cpu_request" {
  description = "CPU request for the PostgreSQL container"
  type        = string
  default     = "250m"
}

variable "postgres_cpu_limit" {
  description = "CPU limit for the PostgreSQL container"
  type        = string
  default     = "500m"
}

# ---------------------------------------------------------------------------
# Resource Limits — RabbitMQ
# ---------------------------------------------------------------------------
variable "rabbitmq_memory_request" {
  description = "Memory request for the RabbitMQ container"
  type        = string
  default     = "256Mi"
}

variable "rabbitmq_memory_limit" {
  description = "Memory limit for the RabbitMQ container"
  type        = string
  default     = "512Mi"
}

variable "rabbitmq_cpu_request" {
  description = "CPU request for the RabbitMQ container"
  type        = string
  default     = "250m"
}

variable "rabbitmq_cpu_limit" {
  description = "CPU limit for the RabbitMQ container"
  type        = string
  default     = "500m"
}
