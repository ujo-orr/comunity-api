# Community API

Spring Boot 기반으로 구현한 **회원 인증 커뮤니티 REST API**입니다.

단순 CRUD 구현을 넘어 JWT 인증·인가, Redis를 이용한 토큰 무효화, 파일 저장소, 예외 처리, 동시성, 테스트와 AWS 배포까지 백엔드 애플리케이션의 전체 흐름을 경험하는 것을 목표로 진행했습니다.

*https://ujo-orr.tistory.com/category/Side%20Project/Community%20API*

## Tech Stack

**Backend**
- Java 21
- Spring Boot 3.5
- Spring Data JPA
- Spring Security
- JWT
- Spring Batch

**Data / Infra**
- MySQL
- Redis
- Amazon S3
- Flyway
- Docker / Docker Compose

**Test**
- JUnit 5
- Mockito
- MockMvc
- Testcontainers

**Deploy**
- AWS EC2
- Nginx
- systemd
- HTTPS

## 주요 기능

- 회원가입, 로그인, 회원 정보 관리
- Access Token / Refresh Token 기반 JWT 인증
- Redis blacklist와 `tokenVersion`을 이용한 기존 토큰 무효화
- 게시글 / 댓글 / 카테고리 CRUD
- 게시글 페이징 및 조회 수 증가
- 게시글 첨부파일 업로드 및 삭제
- 운영 환경 Amazon S3 파일 저장
- 관리자 회원 정지 / 해제 및 회원 관리
- Spring Batch를 이용한 정리 작업
- 공통 예외 처리 및 인증·인가 오류 처리

## 환경 구성

개발 환경과 운영 환경을 분리했습니다.

| 구분 | Local | Production |
| --- | --- | --- |
| Database | H2 | MySQL |
| File Storage | Local | Amazon S3 |
| Schema | JPA | Flyway + `validate` |
| Swagger | 사용 | 비활성화 |

운영 환경에서는 AWS EC2에서 Spring Boot를 `systemd` 서비스로 실행하고, Nginx를 Reverse Proxy로 구성했습니다.

MySQL과 Redis는 Docker Container로 운영하며 healthcheck와 restart 정책을 적용했습니다.

## 테스트

일반 테스트와 실제 MySQL·Redis가 필요한 Testcontainers 기반 테스트를 분리했습니다.

| 구분 | 테스트 | 통과 | 실패 |
| --- | ---: | ---: | ---: |
| `test` | 132 | 132 | 0 |
| `dockerTest` | 25 | 25 | 0 |
| **합계** | **157** | **157** | **0** |

```bash
./gradlew test
./gradlew dockerTest
```

## Deployment

```text
Client
  │ HTTPS
  ▼
Nginx
  │
  ▼
Spring Boot
  ├── MySQL
  ├── Redis
  └── Amazon S3
```

- AWS EC2 · Amazon Linux
- Nginx Reverse Proxy
- Spring Boot `systemd` 서비스
- MySQL · Redis Docker 운영
- HTTPS 적용
- Amazon S3 첨부파일 저장

## 기록

프로젝트의 설계 과정, 문제 해결, 테스트 및 배포 과정은 블로그에 별도로 정리했습니다.
