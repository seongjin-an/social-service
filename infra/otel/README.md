# OpenTelemetry Java Agent

각 서비스의 Dockerfile 이 `infra/otel/opentelemetry-javaagent.jar` 를 `-javaagent` 로 붙인다.
21MB 바이너리라 git 에 넣지 않고(`.gitignore`) 클론 후 내려받는다.

현재 검증된 버전: **2.8.0**

```bash
curl -L -o infra/otel/opentelemetry-javaagent.jar \
  https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.8.0/opentelemetry-javaagent.jar
```

이 파일이 없으면 `docker build` 가 `COPY infra/otel/opentelemetry-javaagent.jar` 단계에서 실패한다.
