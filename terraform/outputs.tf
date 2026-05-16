# ============================================================================
# WorkHub — Terraform Outputs
# ============================================================================

output "namespace" {
  description = "Kubernetes namespace where WorkHub is deployed"
  value       = kubernetes_namespace.workhub.metadata[0].name
}

# ---------------------------------------------------------------------------
# PostgreSQL Outputs
# ---------------------------------------------------------------------------
output "postgres_service_name" {
  description = "Internal DNS name for the PostgreSQL service"
  value       = kubernetes_service.postgres.metadata[0].name
}

output "postgres_connection_string" {
  description = "JDBC connection string for PostgreSQL (cluster-internal)"
  value       = "jdbc:postgresql://${kubernetes_service.postgres.metadata[0].name}:5432/${var.db_name}"
}

# ---------------------------------------------------------------------------
# RabbitMQ Outputs
# ---------------------------------------------------------------------------
output "rabbitmq_service_name" {
  description = "Internal DNS name for the RabbitMQ service"
  value       = kubernetes_service.rabbitmq.metadata[0].name
}

output "rabbitmq_amqp_endpoint" {
  description = "AMQP endpoint for RabbitMQ (cluster-internal)"
  value       = "${kubernetes_service.rabbitmq.metadata[0].name}:5672"
}

output "rabbitmq_management_endpoint" {
  description = "Management UI endpoint for RabbitMQ (cluster-internal)"
  value       = "${kubernetes_service.rabbitmq.metadata[0].name}:15672"
}

# ---------------------------------------------------------------------------
# Application Outputs
# ---------------------------------------------------------------------------
output "app_service_name" {
  description = "Internal DNS name for the WorkHub application service"
  value       = kubernetes_service.workhub_app.metadata[0].name
}

output "app_service_port" {
  description = "Port exposed by the WorkHub application service"
  value       = 80
}

output "app_internal_url" {
  description = "Cluster-internal URL for the WorkHub application"
  value       = "http://${kubernetes_service.workhub_app.metadata[0].name}:80"
}

output "app_replicas" {
  description = "Number of application replicas deployed"
  value       = var.app_replicas
}

# ---------------------------------------------------------------------------
# Deployment Summary
# ---------------------------------------------------------------------------
output "deployment_summary" {
  description = "Summary of deployed resources"
  value = {
    namespace   = kubernetes_namespace.workhub.metadata[0].name
    postgres    = "${kubernetes_service.postgres.metadata[0].name}:5432"
    rabbitmq    = "${kubernetes_service.rabbitmq.metadata[0].name}:5672"
    application = "http://${kubernetes_service.workhub_app.metadata[0].name}:80"
    replicas    = var.app_replicas
  }
}
