# AWS 배포 준비 가이드

이 변경은 코드/설정 준비만 수행한다. AWS 리소스를 생성하거나 실제 AWS로 요청하지 않는다.
첫 배포는 **EC2 호스트에서 Java 21 JAR 실행 + EC2 Docker Redis + RDS MySQL + 비공개 S3**를 기준으로 한다.
애플리케이션 Dockerfile은 이번 범위에서 추가하지 않았다. 기존 Compose는 로컬 MySQL/Redis용이며, 운영 Compose는 Redis만 실행한다.

## 현재 구조와 변경 이유

| 파일 | 역할 / 변경 이유 |
| --- | --- |
| `src/main/resources/application.yml` | 기존 local(H2), docker(MySQL) 설정 유지. 기본 파일 저장소는 local. 기존 prod 문서를 별도 파일로 이동 |
| `src/main/resources/application-prod.yml` | RDS 환경변수, Flyway 실행 + JPA validate, Redis 환경변수, S3 선택, Swagger/H2/샘플 데이터 초기화 비활성화 |
| `build.gradle` | Java 21, Boot 3.5.16, 기존 test/dockerTest 분리 유지. AWS SDK v2 S3 모듈 2.54.17 추가 |
| `attachment/AttachmentStorage.java` | 업로드/다운로드/삭제 공통 계약 |
| `attachment/FileStorageService.java` | 기존 이름과 생성자를 유지한 로컬 구현. local 설정일 때만 빈 등록 |
| `attachment/AttachmentFileValidator.java` | 기존 파일명, MIME 길이, 10MB 제한을 두 구현이 동일하게 사용 |
| `attachment/S3AttachmentStorage.java` | S3 Put/Get/Delete 구현, 기존 오류 코드로 변환 |
| `attachment/S3StorageConfig.java` | 리전과 기본 자격 증명 체인으로 S3Client 생성, 호출 시간 제한 및 종료 처리 |
| `attachment/PostAttachmentService.java`, `post/PostService.java` | 저장소 의존 타입만 인터페이스로 변경. 게시글 삭제 시 파일 삭제도 S3에 적용 |
| `global/config/SecurityConfig.java` | prod에서는 관리자도 Swagger/H2 접근 차단. 다른 API 권한 규칙 유지 |
| `db/migration/V8__create_spring_batch_metadata.sql` | 예약 탈퇴 정리 배치에 필요한 공식 Spring Batch 5.2.6 MySQL 스키마 추가 |
| `docker-compose.prod.yaml` | RDS를 전제로 Redis만 실행. localhost 바인딩, 재시작, AOF 영속화 |
| `.env.example`, `.gitignore` | 값이 없는 환경변수 양식, .env/키/.aws/상태 파일 제외 |
| `S3AttachmentStorageTest`, `ProdDeploymentDockerTest` | AWS 호출 없는 저장 계약 테스트 및 실제 MySQL에서 prod 설정/배치 검증 |

Java 파일은 `src/main/java/org/example/communityapi` 아래, 마이그레이션은 `src/main/resources` 아래에 있다.
기존 V1~V7, JWT/Redis 처리, 회원/댓글/예외 처리, API 요청·응답 스펙은 변경하지 않았다.
테스트 전용 JWT 키와 Testcontainers 비밀번호는 운영 자격 증명이 아니다.

## profile과 파일 저장

| profile | DB | 스키마 관리 | 첨부파일 | Swagger / H2 |
| --- | --- | --- | --- | --- |
| local | 메모리 H2 | JPA create-drop, data.sql | 로컬 uploads | 둘 다 활성화 |
| docker | 로컬 MySQL | 기존 Flyway, JPA validate | 로컬 uploads | Swagger 활성화 / H2 비활성화 |
| prod | RDS MySQL | Flyway V1~V8, JPA validate | S3 | 둘 다 비활성화 + 보안 규칙 차단 |

운영은 `prod` 하나만 활성화한다. local/docker를 함께 활성화하거나 `SPRING_JPA_*`, `SPRINGDOC_*`, `FILE_STORAGE_TYPE` 등의 별도 값으로 운영 설정을 덮어쓰지 않는다.
local H2는 재시작하면 DB 데이터가 사라진다. uploads 파일은 자동 초기화하지 않는 기존 동작을 유지한다.

저장 키는 두 구현 모두 `attachments/posts/{postId}/{UUID}`이며 DB에는 기존처럼 키만 저장한다.
원본 파일명과 콘텐츠 타입은 기존 메타데이터로 보존한다. 기존 다운로드 API가 인증/권한 확인 후 바이트를 반환하며, 공개 S3 URL이나 presigned URL로 변경하지 않았다.
업로드 도중 실패/DB 롤백 시 정리, DB 커밋 후 삭제 정책도 유지한다. 원격 삭제 실패는 로그에 남기며 DB 커밋을 되돌리지 않는다. 정리에 실패한 객체는 운영자가 확인해야 한다.
S3 다운로드는 현재 파일당 10MB 제한을 전제로 메모리에 읽은 뒤 응답한다. 동시 다운로드 수만큼 메모리 사용이 증가하므로 대용량 서비스 확장 시 스트리밍으로 개선할 수 있다.

**기존 로컬 파일은 자동으로 S3에 옮기지 않는다.** 기존 DB를 운영으로 이관한다면 업로드를 잠시 중지하고 `uploads/` 아래 상대 경로를 동일한 S3 객체 키로 복사한 뒤 다운로드를 확인해야 한다. 예: `uploads/attachments/posts/1/UUID` → `attachments/posts/1/UUID`.

## 환경변수

| 이름 | 용도 / 운영 값 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | 운영 `prod`, 개발 `local` 또는 `docker` |
| `DB_URL` | 운영 필수. `jdbc:mysql://RDS_ENDPOINT:3306/community?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&sslMode=VERIFY_IDENTITY` |
| `DB_USERNAME`, `DB_PASSWORD` | 운영 필수. 전용 DB 사용자와 비밀번호. URL에 비밀번호를 넣지 않는다 |
| `JWT_SECRET` | 필수. 임의 생성한 충분히 긴 키(최소 UTF-8 32바이트). 운영/개발 키를 분리하고 재시작마다 바꾸지 않는다 |
| `REDIS_HOST` | 운영 필수. Java를 EC2 호스트에서 실행할 때 `127.0.0.1` |
| `REDIS_PORT` | 기본 `6379` |
| `REDIS_PASSWORD` | 기본 빈 값. 제공된 Redis Compose는 비밀번호 없이 loopback에만 바인딩. 비밀번호를 쓰려면 Redis 서버에도 별도로 같은 인증 설정 필요 |
| `AWS_REGION` | 운영 필수. S3 버킷 리전(예: `ap-northeast-2`) |
| `S3_BUCKET` | 운영 필수. 버킷 이름만 입력 (`s3://` 제외) |
| `AWS_EC2_METADATA_V1_DISABLED` | EC2에서는 `true` 권장. IMDSv2 사용 |
| `FILE_UPLOAD_DIR` | 개발 로컬 저장 경로, 기본 `./uploads`. S3에서는 사용하지 않음 |
| `MYSQL_ROOT_PASSWORD` | 개발용 Compose MySQL에서만 사용. RDS에서는 사용하지 않음 |
| `BOOTSTRAP_ADMIN_ENABLED`, `BOOTSTRAP_SUPERADMIN_ENABLED` | 기존 선택값, 기본 false. 최초 관리자 생성 시에만 true |
| `BOOTSTRAP_ADMIN_PASSWORD`, `BOOTSTRAP_SUPERADMIN_PASSWORD` | 해당 초기화 기능 활성화 시 필수, 기존 12자 이상 제한. 초기 생성 후 기능을 다시 끄고 비밀번호를 환경에서 제거 |
| `SERVER_ADDRESS`, `SERVER_PORT` | Spring Boot 표준 선택값. EC2 리버스 프록시 뒤에서는 `127.0.0.1`, `8080` 사용 |
| `JAVA_TOOL_OPTIONS` | 필요 시 `-Duser.timezone=Asia/Seoul` 및 RDS CA truststore JVM 옵션 설정 |

`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`를 앱 설정에 넣지 않는다. EC2에 S3 권한 IAM Role을 연결하고 SDK 기본 자격 증명 체인이 임시 자격 증명을 가져오게 한다. 환경변수/홈 디렉터리에 과거 정적 AWS 키가 남아 있으면 기본 체인이 먼저 사용할 수 있으므로 제거한다.
[공식 자격 증명 체인 문서](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html), [EC2 IAM Role 문서](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/ec2-iam-roles.html)

## 로컬 실행

Java 21과 Docker Engine/Desktop이 필요하다. 다음 명령은 사용자가 실행하는 절차이며 AWS 리소스를 만들지 않는다.

```bash
cp .env.example .env
chmod 600 .env
# .env의 JWT_SECRET을 직접 생성한 값으로 채운다(예: openssl rand -base64 48).
# 비밀번호처럼 특수문자가 있는 값은 작은따옴표로 감싼다.
set -a
source .env
set +a
docker compose up -d redis
./gradlew bootRun --args='--spring.profiles.active=local'
```

`.env`는 Spring Boot가 자동 로드하지 않는다. 위처럼 현재 셸에 export하거나 IDE 실행 환경변수에 등록한다. `source`는 본인이 작성한 파일에만 사용한다.
MySQL로 개발하려면 `.env`의 `MYSQL_ROOT_PASSWORD`, `DB_PASSWORD`를 동일한 로컬 전용 값으로 채운 뒤 환경변수를 다시 로드한다.

```bash
docker compose up -d mysql redis
./gradlew bootRun --args='--spring.profiles.active=docker'
```

이미 초기화된 MySQL 볼륨의 비밀번호는 환경변수만 바꿔도 갱신되지 않는다. 기존 데이터를 삭제하는 대신 DB에서 계정 설정을 확인한다.

## 운영 실행 (AWS 준비가 완료된 뒤)

EC2에는 Java 21과 Docker Compose를 설치한다. 로컬/CI에서 빌드한 JAR를 EC2로 전달한다.

```bash
./gradlew test dockerTest bootJar
```

생성물: `build/libs/community-api-0.0.1-SNAPSHOT.jar`.
EC2에서 `.env.example`을 `.env.prod`로 복사하고 profile, RDS, JWT, Redis, S3 값을 입력한다. Redis host는 `127.0.0.1`로 설정한다. EC2 Role을 연결하고 RDS TLS 신뢰 설정을 먼저 완료한다.

```bash
chmod 600 .env.prod
set -a
source .env.prod
set +a
export SERVER_ADDRESS=127.0.0.1
export JAVA_TOOL_OPTIONS='-Duser.timezone=Asia/Seoul'
docker compose -f docker-compose.prod.yaml up -d redis
java -jar community-api-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

이 명령은 최초 전경 실행 확인용이다. 확인 후 전용 Linux 계정 + systemd 서비스로 등록하여 재부팅 시 시작하고 로그를 보관한다. 서비스 환경 파일도 권한 600으로 관리하고 `WorkingDirectory`, `ExecStart`, 환경변수를 지정한다. JVM에 별도 truststore 옵션을 쓰는 경우 위 `JAVA_TOOL_OPTIONS`에 함께 포함한다.
EC2의 Nginx 등에서 HTTPS를 종료하고 localhost:8080으로 프록시한다. 현재 요청 최대 크기가 50MB이므로 프록시 업로드 제한도 맞춘다. 외부에 8080을 직접 개방하지 않는다.

운영 JDBC URL 예시는 TLS 서버 인증을 검증하는 `VERIFY_IDENTITY`를 사용한다. AWS RDS CA 인증서를 공식 배포처에서 받아 애플리케이션 JVM이 사용하는 truststore에 등록해야 한다. RDS 엔드포인트 이름으로 접속하고 검증 실패를 `useSSL=false`로 우회하지 않는다. 별도 truststore를 쓰면 `javax.net.ssl.trustStore`와 필요한 암호를 JVM 실행 환경에서 지정하며 이미지/저장소에 비밀번호를 넣지 않는다.
[RDS MySQL 인증서 및 Java truststore 설정](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/ssl-certificate-rotation-mysql.html)

## AWS Console에서 직접 할 작업과 다음 순서

1. 리전과 예산 알림을 정하고 EC2/RDS/S3를 같은 리전으로 준비한다. 인스턴스·스토리지·공인 IP 등의 요금과 현재 무료 사용 조건은 생성 화면에서 직접 확인한다.
2. EC2와 RDS가 통신할 VPC/서브넷을 준비한다. RDS는 public access를 끄고 MySQL 8.4 호환 인스턴스로 준비한다. RDS 보안 그룹 3306 인바운드는 EC2 보안 그룹만 허용한다.
3. RDS에 `community` 데이터베이스와 앱 전용 계정을 준비한다. 첫 실행에서 Flyway가 테이블을 생성하므로 해당 DB에 DDL/DML 권한이 필요하다. RDS master 계정을 앱 런타임 계정으로 사용하지 않는다. DB 백업/스냅샷 정책을 정한다.
4. 빈 DB에는 V1~V8이 순서대로 적용된다. 기존 스키마를 가져올 때는 Flyway 이력도 확인한다. prod는 의도치 않은 기존 DB 수용을 막기 위해 baseline-on-migrate=false다. 기존에 `BATCH_*` 테이블을 수동 생성했다면 V8과 충돌하므로 스키마/이력을 검토한 뒤 별도 이관 계획을 세운다. 기존 마이그레이션 내용을 바꾸거나 이력을 임의로 삭제하지 않는다.
5. 비공개 S3 버킷을 생성한다. Block Public Access 전체 활성화, Object Ownership은 Bucket owner enforced(ACL 비활성화), 기본 암호화 SSE-S3를 사용한다. 버킷 정책으로 공개 읽기를 허용하지 않는다. 버전 관리를 켜면 삭제된 객체의 이전 버전은 남으므로 수명 주기를 함께 정한다.
6. EC2용 IAM Role을 만들어 아래 권한을 부여하고 인스턴스에 연결한다. IMDSv2를 필수로 설정한다. EC2 외부 인바운드는 HTTPS와 필요한 관리 접속만 허용한다(SSH 사용 시 본인 IP 한정). Redis 6379/MySQL 3306은 인터넷에 개방하지 않는다. S3 HTTPS와 RDS로 나갈 네트워크 경로도 확인한다.
7. RDS CA 신뢰 설정 → Redis 시작 → 환경변수 등록 → JAR 기동 → Flyway/JPA 시작 로그 확인 순서로 진행한다.
8. HTTPS에서 회원가입/로그인/게시글/댓글/첨부 업로드·다운로드·삭제/로그아웃을 확인한다. S3 객체 키와 UUID를 확인하고, Swagger/H2 접근 차단 및 서버 재시작 후 Redis/파일 유지도 점검한다.
9. systemd와 HTTPS 프록시를 적용하고 로그/DB 백업/배포 롤백 절차를 정리한다. 다음 개선은 CI 빌드·배포 자동화이며, ElastiCache 도입은 별도 단계다.

EC2 Role에 사용할 정책 예시(실제 버킷 이름으로 교체):

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
      "Resource": "arn:aws:s3:::YOUR_BUCKET/attachments/posts/*"
    },
    {
      "Effect": "Allow",
      "Action": "s3:ListBucket",
      "Resource": "arn:aws:s3:::YOUR_BUCKET"
    }
  ]
}
```

첨부파일 전용 버킷을 사용한다. ListBucket 권한은 존재하지 않는 객체 요청이 404로 구분되게 한다. 권한이 없으면 S3는 403을 반환할 수 있고 앱은 이를 저장소 오류로 처리한다.
[S3 GetObject 오류/권한 설명](https://docs.aws.amazon.com/AmazonS3/latest/API/API_GetObject.html)
SSE-KMS를 선택한다면 KMS 권한과 키 정책도 필요하므로 위 SSE-S3 기준 설정과 구분한다.

## 테스트와 검증 범위

```bash
./gradlew test          # docker 태그 제외, H2/단위/보안 테스트
./gradlew dockerTest    # 실제 일회용 MySQL/Redis 컨테이너 (Docker 실행 필요)
./gradlew bootJar       # 실행 JAR 빌드
```

기존 테스트를 삭제하거나 검증을 약화하지 않았다. S3는 Mockito로 Put/Get/Delete, UUID 키, 콘텐츠/파일명 검증, 오류 변환 및 실패 정리를 검사한다. prod 통합 테스트는 실제 MySQL에서 운영 profile 파일을 읽고 S3 빈 선택, V1~V8 마이그레이션, 예약 배치 실행, 관리자 Swagger/H2 차단을 검사한다. 실제 S3/IAM/RDS 네트워크/TLS 연결은 AWS 구성 후 별도로 검증해야 한다.

### 이번 작업의 실행 결과

- `./gradlew test`: **104개 통과, 실패/스킵 0개**.
- `./gradlew dockerTest`: **3개 통과, 실패/스킵 0개**. 기존 Docker E2E와 prod 테스트 2개 모두 통과.
- `CommunityFlowDockerTest`는 공개 게시글 조회 정책에 맞게 수정했다. 로그아웃 전 `/api/members/me`는 200, 로그아웃 후 같은 Access Token으로 공개 게시글 조회는 200(조회수 증가), `/api/members/me`는 401인지 각각 확인한다. 기존 Redis 블랙리스트/TTL 검증과 회원가입부터 로그아웃까지의 E2E 흐름은 유지한다.
- Spring Batch V8 마이그레이션은 유지한다. 이번 테스트 정책 수정에서는 Security/JWT 로직과 API 권한을 변경하지 않았다.
- `./gradlew bootJar`: 성공.
- `docker compose -f docker-compose.prod.yaml config --quiet`: 성공.
- `git diff --check`: 성공. `.env`, `.env.prod`, `.aws/credentials`, PEM 및 운영 secret 파일 제외 규칙 확인.
- 실제 AWS 리소스 생성/배포/S3 요청은 하지 않았다. RDS 인증서, IAM Role, S3 권한, AWS 네트워크 동작은 아직 미검증이다.
