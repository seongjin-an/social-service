plugins {
    id("org.springframework.boot") version "3.3.5"
}

dependencies {
    implementation(project(":common"))
    // 웹 서버(8084 bind) + Eureka 클라이언트 TransportClientFactories 빈 활성화에 필요.
    // 없으면 non-web 컨텍스트라 eurekaAutoServiceRegistration 이 NPE 로 크래시.
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.kafka:spring-kafka")

    implementation("org.springframework.boot:spring-boot-starter-validation")

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // Eureka 제거: fanout 은 순수 Kafka/Redis 워커라 서비스 디스커버리 in/out 이 없음.
    // (게이트웨이가 라우팅하지도, 다른 서비스를 HTTP 로 부르지도 않음)
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    implementation("io.micrometer:micrometer-registry-prometheus")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
