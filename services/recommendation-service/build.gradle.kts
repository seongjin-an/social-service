plugins {
    id("org.springframework.boot") version "3.3.5"
}

// 소유 테이블이 없는 서비스 — 입력은 전부 Redis(geo/카드/선호/seen)와 Kafka(like-relay)다.
// 그래서 data-jpa / mysql-connector 를 넣지 않는다(넣으면 DataSource 없다고 기동이 실패한다).
dependencies {
    implementation(project(":common"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")

    implementation("io.micrometer:micrometer-registry-prometheus")

    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
