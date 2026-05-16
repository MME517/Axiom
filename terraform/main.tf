# ============================================================================
# WorkHub — Terraform Infrastructure as Code (Kubernetes Track)
# Provider: hashicorp/kubernetes
# Target  : Local Minikube cluster
# Usage   : terraform init && terraform plan && terraform apply
# ============================================================================

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.30"
    }
  }
}

# ---------------------------------------------------------------------------
# Provider — connects to the cluster defined in ~/.kube/config
# ---------------------------------------------------------------------------
provider "kubernetes" {
  config_path    = var.kubeconfig_path
  config_context = var.kubeconfig_context
}

# ---------------------------------------------------------------------------
# 1. Namespace
# ---------------------------------------------------------------------------
resource "kubernetes_namespace" "workhub" {
  metadata {
    name = var.namespace

    labels = {
      app        = "workhub"
      managed-by = "terraform"
      phase      = "3"
    }
  }
}

# ---------------------------------------------------------------------------
# 2. Secret — credentials for Postgres, RabbitMQ, JWT
# ---------------------------------------------------------------------------
resource "kubernetes_secret" "workhub_secret" {
  metadata {
    name      = "workhub-secret"
    namespace = kubernetes_namespace.workhub.metadata[0].name
  }

  type = "Opaque"

  data = {
    SPRING_DATASOURCE_USERNAME = var.db_username
    SPRING_DATASOURCE_PASSWORD = var.db_password
    SPRING_RABBITMQ_USERNAME   = var.rabbitmq_username
    SPRING_RABBITMQ_PASSWORD   = var.rabbitmq_password
    JWT_SECRET                 = var.jwt_secret
  }
}

# ---------------------------------------------------------------------------
# 3. ConfigMap — non-secret application configuration
# ---------------------------------------------------------------------------
resource "kubernetes_config_map" "workhub_config" {
  metadata {
    name      = "workhub-config"
    namespace = kubernetes_namespace.workhub.metadata[0].name
  }

  data = {
    SPRING_DATASOURCE_URL  = "jdbc:postgresql://postgres-service:5432/${var.db_name}"
    SPRING_RABBITMQ_HOST   = "rabbitmq-service"
    SPRING_RABBITMQ_PORT   = "5672"
    SPRING_PROFILES_ACTIVE = var.spring_profile
    SERVER_PORT            = tostring(var.app_port)
  }
}

# ---------------------------------------------------------------------------
# 4. PostgreSQL Deployment + Service
# ---------------------------------------------------------------------------
resource "kubernetes_deployment" "postgres" {
  metadata {
    name      = "postgres"
    namespace = kubernetes_namespace.workhub.metadata[0].name

    labels = {
      app        = "postgres"
      managed-by = "terraform"
    }
  }

  spec {
    replicas = 1

    selector {
      match_labels = {
        app = "postgres"
      }
    }

    template {
      metadata {
        labels = {
          app = "postgres"
        }
      }

      spec {
        container {
          name  = "postgres"
          image = var.postgres_image

          port {
            container_port = 5432
          }

          env {
            name  = "POSTGRES_DB"
            value = var.db_name
          }

          env {
            name = "POSTGRES_USER"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.workhub_secret.metadata[0].name
                key  = "SPRING_DATASOURCE_USERNAME"
              }
            }
          }

          env {
            name = "POSTGRES_PASSWORD"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.workhub_secret.metadata[0].name
                key  = "SPRING_DATASOURCE_PASSWORD"
              }
            }
          }

          volume_mount {
            name       = "postgres-storage"
            mount_path = "/var/lib/postgresql/data"
          }

          resources {
            requests = {
              memory = var.postgres_memory_request
              cpu    = var.postgres_cpu_request
            }
            limits = {
              memory = var.postgres_memory_limit
              cpu    = var.postgres_cpu_limit
            }
          }
        }

        volume {
          name = "postgres-storage"
          empty_dir {}
        }
      }
    }
  }
}

resource "kubernetes_service" "postgres" {
  metadata {
    name      = "postgres-service"
    namespace = kubernetes_namespace.workhub.metadata[0].name
  }

  spec {
    selector = {
      app = "postgres"
    }

    port {
      protocol    = "TCP"
      port        = 5432
      target_port = 5432
    }

    type = "ClusterIP"
  }
}

# ---------------------------------------------------------------------------
# 5. RabbitMQ Deployment + Service
# ---------------------------------------------------------------------------
resource "kubernetes_deployment" "rabbitmq" {
  metadata {
    name      = "rabbitmq"
    namespace = kubernetes_namespace.workhub.metadata[0].name

    labels = {
      app        = "rabbitmq"
      managed-by = "terraform"
    }
  }

  spec {
    replicas = 1

    selector {
      match_labels = {
        app = "rabbitmq"
      }
    }

    template {
      metadata {
        labels = {
          app = "rabbitmq"
        }
      }

      spec {
        container {
          name  = "rabbitmq"
          image = var.rabbitmq_image

          port {
            container_port = 5672
            name           = "amqp"
          }

          port {
            container_port = 15672
            name           = "management"
          }

          env {
            name = "RABBITMQ_DEFAULT_USER"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.workhub_secret.metadata[0].name
                key  = "SPRING_RABBITMQ_USERNAME"
              }
            }
          }

          env {
            name = "RABBITMQ_DEFAULT_PASS"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.workhub_secret.metadata[0].name
                key  = "SPRING_RABBITMQ_PASSWORD"
              }
            }
          }

          resources {
            requests = {
              memory = var.rabbitmq_memory_request
              cpu    = var.rabbitmq_cpu_request
            }
            limits = {
              memory = var.rabbitmq_memory_limit
              cpu    = var.rabbitmq_cpu_limit
            }
          }
        }
      }
    }
  }
}

resource "kubernetes_service" "rabbitmq" {
  metadata {
    name      = "rabbitmq-service"
    namespace = kubernetes_namespace.workhub.metadata[0].name
  }

  spec {
    selector = {
      app = "rabbitmq"
    }

    port {
      name        = "amqp"
      protocol    = "TCP"
      port        = 5672
      target_port = 5672
    }

    port {
      name        = "management"
      protocol    = "TCP"
      port        = 15672
      target_port = 15672
    }

    type = "ClusterIP"
  }
}

# ---------------------------------------------------------------------------
# 6. WorkHub Application Deployment + Service
# ---------------------------------------------------------------------------
resource "kubernetes_deployment" "workhub_app" {
  metadata {
    name      = "workhub-app"
    namespace = kubernetes_namespace.workhub.metadata[0].name

    labels = {
      app        = "workhub-app"
      managed-by = "terraform"
    }
  }

  spec {
    replicas = var.app_replicas

    selector {
      match_labels = {
        app = "workhub-app"
      }
    }

    template {
      metadata {
        labels = {
          app = "workhub-app"
        }
      }

      spec {
        container {
          name              = "workhub-app"
          image             = var.app_image
          image_pull_policy = var.image_pull_policy

          port {
            container_port = var.app_port
          }

          env_from {
            config_map_ref {
              name = kubernetes_config_map.workhub_config.metadata[0].name
            }
          }

          env_from {
            secret_ref {
              name = kubernetes_secret.workhub_secret.metadata[0].name
            }
          }

          # Liveness probe — restarts container if app hangs
          liveness_probe {
            http_get {
              path = "/actuator/health/liveness"
              port = var.app_port
            }
            initial_delay_seconds = 90
            period_seconds        = 30
            failure_threshold     = 3
          }

          # Readiness probe — removes from service until dependencies ready
          readiness_probe {
            http_get {
              path = "/actuator/health/readiness"
              port = var.app_port
            }
            initial_delay_seconds = 60
            period_seconds        = 10
            failure_threshold     = 3
          }

          resources {
            requests = {
              memory = var.app_memory_request
              cpu    = var.app_cpu_request
            }
            limits = {
              memory = var.app_memory_limit
              cpu    = var.app_cpu_limit
            }
          }
        }
      }
    }
  }

  depends_on = [
    kubernetes_deployment.postgres,
    kubernetes_deployment.rabbitmq,
  ]
}

resource "kubernetes_service" "workhub_app" {
  metadata {
    name      = "workhub-service"
    namespace = kubernetes_namespace.workhub.metadata[0].name
  }

  spec {
    selector = {
      app = "workhub-app"
    }

    port {
      protocol    = "TCP"
      port        = 80
      target_port = var.app_port
    }

    type = var.service_type
  }
}
