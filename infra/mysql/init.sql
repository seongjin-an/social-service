-- MySQL 초기화 (첫 부팅 시 1회 실행 · docker-entrypoint-initdb.d)
-- 1) Debezium CDC 용 replication 유저
-- 2) message-service 가 쓰는 social DB 보장 (compose 는 social 만 생성)

CREATE USER IF NOT EXISTS 'debezium'@'%' IDENTIFIED BY 'dbz_pw';
GRANT SELECT, RELOAD, SHOW DATABASES, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'debezium'@'%';

CREATE DATABASE IF NOT EXISTS social CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON social.* TO 'dev_user'@'%';

FLUSH PRIVILEGES;
