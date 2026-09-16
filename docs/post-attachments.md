# 게시글에 첨부파일 기능 추가하기

게시판 프로젝트에 게시글, 댓글, 좋아요 기능을 만든 뒤에는 첨부파일 기능도 추가해 보았습니다. 사용자는 게시글 하나에 이미지, PDF, 문서 같은 파일을 여러 개 올릴 수 있습니다.

처음에는 파일을 DB에 바로 넣을지 고민했습니다. 하지만 파일까지 DB에 저장하면 DB 용량이 빠르게 커지고, 백업이나 조회도 무거워질 수 있습니다. 그래서 이번에는 **실제 파일은 서버 디스크에 저장하고, DB에는 파일 정보만 저장**하는 방식으로 구현했습니다.

## 전체 구조

첨부파일은 게시글에 여러 개 달릴 수 있습니다.

```text
Post 1 : N PostAttachment
```

`PostAttachment`는 파일 하나를 의미합니다. 이 엔티티에는 다음 정보를 저장합니다.

- 어떤 게시글의 파일인지 (`post_id`)
- 사용자가 올린 원본 파일명
- 서버 또는 오브젝트 스토리지에서 파일을 찾는 저장 키(`storageKey`)
- 파일 타입(MIME type)
- 파일 크기
- 업로드 시간

파일 내용 자체는 `./uploads` 폴더에 저장됩니다. 운영 환경에서는 `FILE_UPLOAD_DIR` 환경 변수로 저장 위치를 변경할 수 있게 했습니다.

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "post_id", nullable = false)
private Post post;

@Column(nullable = false, length = 255)
private String originalFileName;

@Column(nullable = false, unique = true, length = 255)
private String storageKey;
```

원본 파일명과 저장 키를 나눈 것이 핵심입니다. 예를 들어 사용자가 `resume.pdf`를 업로드했다고 해도, 서버에는 게시글 번호와 UUID를 조합한 키로 저장합니다.

```text
원본 파일명: resume.pdf
storageKey: attachments/posts/42/2e6935d0-752d-4a8e-a1eb-521f9bb1a216
```

이렇게 하면 여러 사용자가 똑같이 `image.png`를 올려도 파일이 덮어써지지 않습니다. 서버 경로에 사용자가 보낸 파일명을 그대로 쓰지 않기 때문에 경로 조작 위험도 줄일 수 있습니다.

## API 설계

첨부파일 API는 게시글 하위 리소스로 만들었습니다. 파일은 게시글 없이 존재할 수 없기 때문입니다.

| 기능 | 요청 | 권한 |
| --- | --- | --- |
| 파일 업로드 | `POST /api/posts/{postId}/attachments` | 게시글 작성자 |
| 파일 목록 조회 | `GET /api/posts/{postId}/attachments` | 로그인 사용자 |
| 파일 다운로드 | `GET /api/posts/{postId}/attachments/{attachmentId}/download` | 로그인 사용자 |
| 파일 삭제 | `DELETE /api/posts/{postId}/attachments/{attachmentId}` | 게시글 작성자 |

업로드 요청은 JSON이 아니라 `multipart/form-data` 형식입니다. 파일은 여러 개 보낼 수 있고, 파라미터 이름은 `files`로 정했습니다.

```bash
curl -X POST http://localhost:8080/api/posts/1/attachments \
  -H 'Authorization: Bearer <access-token>' \
  -F 'files=@/path/to/guide.pdf' \
  -F 'files=@/path/to/image.png'
```

Spring Controller에서는 `MultipartFile` 목록으로 받습니다.

```java
@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<List<AttachmentResponse>> upload(
        @PathVariable Long postId,
        @RequestParam("files") List<MultipartFile> files,
        Authentication authentication) {
    return ResponseEntity.ok(
            attachmentService.upload(postId, files, authentication.getName())
    );
}
```

`Authentication`에서 로그인한 사용자의 이메일을 가져온 뒤, 게시글 작성자의 이메일과 비교합니다. 따라서 다른 사용자가 파일을 마음대로 추가하거나 삭제할 수 없습니다.

## 파일 저장 흐름

파일 업로드는 다음 순서로 처리합니다.

```text
1. 게시글이 존재하는지 확인한다.
2. 로그인한 사용자가 게시글 작성자인지 확인한다.
3. 파일이 비어 있지 않고 크기 제한을 넘지 않는지 확인한다.
4. `attachments/posts/{게시글ID}/{UUID}` 키로 실제 파일을 uploads 폴더에 저장한다.
5. 파일의 메타데이터를 post_attachments 테이블에 저장한다.
6. 응답으로 다운로드 URL과 파일 정보를 돌려준다.
```

파일 하나는 최대 10MB, 한 요청 전체는 최대 50MB로 제한했습니다.

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 50MB
```

컨트롤러 제한만 믿지 않고 파일 저장 서비스에서도 빈 파일과 10MB 초과 파일을 한 번 더 검사했습니다. 설정이 바뀌거나 서비스가 다른 곳에서 호출되는 경우에도 최소한의 방어가 되기 때문입니다.

## 다운로드할 때 원본 파일명을 보여주는 방법

서버에는 UUID 파일명으로 저장했지만, 사용자가 다운로드할 때는 원래 이름으로 받아야 자연스럽습니다. 그래서 다운로드 응답에 `Content-Disposition` 헤더를 넣었습니다.

```java
.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
        .filename(attachment.getOriginalFileName(), StandardCharsets.UTF_8)
        .build().toString())
```

`UTF-8`을 지정했기 때문에 `프로젝트_설명서.pdf`처럼 한글 파일명도 정상적으로 내려받을 수 있습니다.

## 파일 삭제가 생각보다 조심스러운 이유

첨부파일을 삭제할 때는 DB 행과 실제 디스크 파일을 모두 지워야 합니다. 여기서 순서가 중요합니다.

만약 디스크 파일을 먼저 지웠는데 그 뒤 DB 삭제가 실패하고 트랜잭션이 롤백되면, DB에는 첨부파일 정보가 남아 있지만 실제 파일은 없는 상태가 됩니다.

그래서 이번 구현에서는 먼저 DB 삭제를 처리하고, **DB 트랜잭션이 정상적으로 커밋된 뒤에** 실제 파일을 지우도록 했습니다.

```java
attachmentRepository.delete(attachment);

TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override
    public void afterCommit() {
        fileStorageService.deleteQuietly(attachment.getStoredFileName());
    }
});
```

이 방식이라면 DB 작업이 실패했을 때 실제 파일은 그대로 남아 있으므로, 파일만 먼저 사라지는 문제를 막을 수 있습니다. 반대로 커밋 후 파일 삭제가 실패할 수 있으므로, 현재는 오류 로그를 남기게 했습니다. 서비스 규모가 커진다면 실패한 파일 삭제 작업을 재시도하는 배치 작업이나 큐를 추가할 수 있습니다.

## 게시글을 삭제하면 첨부파일은 어떻게 될까?

게시글이 삭제됐는데 첨부파일 정보만 남으면 안 됩니다. 그래서 DB 외래 키에 `ON DELETE CASCADE`를 추가했습니다.

```sql
CONSTRAINT fk_post_attachments_post
    FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
```

이제 게시글이 삭제되면 `post_attachments` 테이블의 관련 행도 함께 삭제됩니다. 실제 디스크 파일은 게시글 삭제 서비스에서 파일 목록을 먼저 확인해 둔 뒤, 게시글 삭제 커밋 후에 정리합니다.

기존 Flyway 마이그레이션 파일은 수정하지 않고 새 파일을 만들었습니다.

```text
V5__create_post_attachments.sql
```

이미 운영 DB에 적용된 마이그레이션을 수정하면 checksum 오류가 날 수 있기 때문에, 기능을 추가할 때 새 버전을 만드는 습관이 중요합니다.

## 마무리

첨부파일 기능은 단순히 `MultipartFile`을 받는 것에서 끝나지 않았습니다. 파일 이름 충돌, 용량 제한, 작성자 권한, DB와 디스크의 삭제 순서까지 함께 고민해야 했습니다.

이번 구현은 서버 디스크를 스토리지로 사용했지만, 나중에는 `FileStorageService`의 역할을 유지한 채 내부 구현을 S3 같은 외부 스토리지로 바꿀 수 있습니다. DB에 저장한 `storageKey`는 S3의 object key로도 그대로 사용할 수 있으므로, 스토리지 변경도 비교적 쉽게 할 수 있습니다.
