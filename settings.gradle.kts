pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "social-discovery"

// ── 라이브러리 모듈 (libs/) ────────────────────────────────────────────────
include("common")
project(":common").projectDir = file("libs/common")

// ── 서비스 모듈 (services/) ────────────────────────────────────────────────
// 물리적으로 services/ 하위에 있으므로 projectDir 을 명시적으로 매핑한다.
val services = listOf(
    "eureka-server",
    "api-gateway",
    "user-service",
    "profile-service"
//    "connection-service",
//    "message-service",
//    "fanout-delivery-service",
)

services.forEach { name ->
    include(name)
    project(":$name").projectDir = file("services/$name")
}
