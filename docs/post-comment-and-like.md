# 게시글에 댓글과 좋아요 기능 추가하기

게시판 프로젝트를 만들면서 게시글 작성, 조회, 수정, 삭제 기능까지 먼저 구현했습니다. 이번에는 게시글에 댓글과 좋아요 기능을 추가했습니다.

처음에는 댓글이나 좋아요를 `Post` 엔티티 안에 넣어도 될지 고민했습니다. 다만 둘 다 게시글과 분리해서 관리하는 편이 더 자연스럽다고 판단했습니다. 특히 작성자, 작성 시간, 수정 시간 같은 정보가 필요하기 때문에 각각 엔티티로 만들었습니다.

## 댓글 기능

댓글은 한 명의 회원이 작성하고, 하나의 게시글에 달립니다.

```text
Member 1 : N Comment N : 1 Post
```

<!-- '댓글과 회원·게시글의 관계를 표시한 ERD 사진' -->

그래서 `Comment` 엔티티에 아래 내용을 넣었습니다.

- 댓글 ID
- 게시글 정보 (`Post`)
- 작성자 정보 (`Member`)
- 댓글 내용
- 생성 시간, 수정 시간

시간 정보는 기존에 사용하던 `BaseTimeEntity`를 상속받아 처리했습니다. 댓글을 수정할 때는 내용만 변경하고, 수정 시간은 JPA Auditing으로 자동 갱신되도록 했습니다.

댓글 API는 다음처럼 만들었습니다.

| 기능 | 요청 |
| --- | --- |
| 댓글 작성 | `POST /api/posts/{postId}/comments` |
| 댓글 목록 조회 | `GET /api/posts/{postId}/comments` |
| 댓글 수정 | `PATCH /api/comments/{commentId}` |
| 댓글 삭제 | `DELETE /api/comments/{commentId}` |

<!-- 'Swagger 또는 Postman에서 댓글 API를 테스트한 사진' -->

댓글 수정과 삭제에서는 로그인한 사용자의 이메일과 댓글 작성자의 이메일을 비교했습니다. 이메일이 다르면 권한이 없다는 응답을 주도록 했습니다. 다른 사용자가 작성한 댓글을 수정하거나 삭제하면 안 되기 때문입니다.

댓글 목록을 가져올 때는 댓글 내용뿐 아니라 작성자 닉네임도 같이 보여주도록 했습니다. 목록을 조회할 때 댓글마다 작성자를 다시 조회하지 않도록 JPQL DTO 조회를 사용했습니다.

## CommentRepository에서 DTO Projection과 Fetch Join을 나눈 이유

댓글 기능에서는 조회 목적에 따라 Repository 쿼리를 두 가지 방식으로 나눴습니다.

댓글 **목록 조회**에는 DTO Projection을 사용했습니다.

```java
SELECT new org.example.communityapi.comment.dto.CommentResponse(
    c.id, c.content, m.nickname, c.createdAt, c.updatedAt
)
FROM Comment c
JOIN c.member m
WHERE c.post.id = :postId
ORDER BY c.createdAt ASC
```

목록 화면에서 필요한 값은 댓글 ID, 내용, 작성자 닉네임, 작성·수정 시간뿐입니다. 이때 `Comment` 엔티티와 `Member` 엔티티 전체를 조회한 뒤 서비스에서 다시 DTO로 바꾸는 것보다, 처음부터 `CommentResponse`에 필요한 값만 담아 가져오는 편이 단순하다고 생각했습니다.

또한 댓글마다 작성자 닉네임을 가져오기 위해 추가 쿼리가 여러 번 나가는 문제도 피할 수 있습니다. 목록을 보여주는 용도이고 엔티티를 수정할 일도 없어서 DTO Projection이 잘 맞았습니다.

반대로 댓글 **수정과 삭제**에는 Fetch Join을 사용했습니다.

```java
SELECT c FROM Comment c JOIN FETCH c.member WHERE c.id = :id
```

수정과 삭제를 하려면 먼저 댓글 엔티티를 가져와야 합니다. 그리고 로그인한 사용자가 댓글 작성자인지 확인하기 위해 `comment.getMember().getEmail()`도 필요합니다.

`member`는 지연 로딩(`LAZY`)으로 설정되어 있습니다. Fetch Join 없이 댓글만 먼저 가져오면 작성자 정보를 확인하는 순간 회원 조회 쿼리가 한 번 더 실행될 수 있습니다. 여기서는 댓글 한 건과 작성자 정보가 항상 같이 필요하므로 Fetch Join으로 한 번에 조회하도록 했습니다.

정리하면 목록처럼 화면에 보여주기만 하는 데이터는 DTO Projection으로 필요한 값만 조회했고, 수정·삭제처럼 엔티티 변경과 작성자 검증이 필요한 경우에는 Fetch Join으로 엔티티와 작성자 정보를 같이 조회했습니다.

<!-- 'CommentRepository의 DTO Projection과 Fetch Join 코드가 보이는 사진' -->

## 게시글 삭제 시 댓글 처리

게시글이 삭제됐는데 댓글만 남아 있으면 데이터가 이상해질 수 있습니다. 그래서 댓글 테이블의 `post_id` 외래 키에 `ON DELETE CASCADE`를 설정했습니다.

```sql
FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
```

이제 게시글이 삭제되면 그 게시글에 달린 댓글도 DB에서 같이 삭제됩니다. 서비스 코드에서 댓글을 하나씩 지우지 않아도 됩니다.

<!-- '게시글 삭제 전후 댓글 데이터가 함께 삭제된 것을 보여주는 사진' -->

## 좋아요 기능

좋아요는 댓글과 조금 다르게 생각했습니다. 좋아요 자체에는 보통 긴 내용이 없고, “어떤 회원이 어떤 게시글을 좋아했는지”만 저장하면 됩니다.

그래서 회원과 게시글 사이를 연결하는 `post_likes` 테이블을 만들었습니다.

```text
Member 1 : N PostLike N : 1 Post
```

<!-- '좋아요 테이블의 member_id, post_id 복합 키가 보이는 ERD 사진' -->

여기서는 `member_id`와 `post_id`를 묶어서 복합 PK로 사용했습니다.

```sql
PRIMARY KEY (member_id, post_id)
```

이렇게 하면 한 회원이 같은 게시글에 좋아요를 여러 번 누르는 것을 DB에서도 막을 수 있습니다. 좋아요는 별도 번호를 꼭 만들 필요가 없는 연결 데이터라서 복합 키가 잘 맞는다고 생각했습니다.

JPA에서는 `PostLikeId`를 `@Embeddable`로 만들고, `PostLike` 엔티티에서 `@EmbeddedId`로 사용했습니다. 회원과 게시글 관계는 `@MapsId`로 복합 키와 연결했습니다.

좋아요 API는 다음과 같습니다.

| 기능 | 요청 |
| --- | --- |
| 좋아요 | `POST /api/posts/{postId}/likes` |
| 좋아요 취소 | `DELETE /api/posts/{postId}/likes` |
| 좋아요 수 조회 | `GET /api/posts/{postId}/likes/count` |

<!-- '좋아요 등록, 취소, 개수 조회 API 테스트 사진' -->

이미 좋아요한 게시글에 다시 좋아요를 요청하면 `409 Conflict`를 반환하도록 했습니다. 좋아요 취소는 해당 좋아요 기록이 있을 때만 가능합니다.

좋아요도 게시글이나 회원이 삭제되면 같이 없어져야 합니다. 그래서 `member_id`, `post_id` 외래 키에 둘 다 `ON DELETE CASCADE`를 적용했습니다.

## Flyway 마이그레이션으로 테이블 추가

테이블을 추가하면서 기존 마이그레이션 파일을 수정하지 않고 새 버전을 만들었습니다.

- `V3__createComments.sql`: 댓글 테이블 생성
- `V4__create_post_likes.sql`: 좋아요 테이블 생성

이미 적용된 Flyway 파일을 수정하면 checksum 오류가 날 수 있습니다. 그래서 기능을 추가할 때마다 새 마이그레이션 파일을 만드는 방식으로 진행했습니다.

<!-- 'Flyway 마이그레이션 파일 목록 또는 실행 결과 사진' -->

## 다음으로 첨부파일 기능을 추가할 예정입니다

다음 기능은 첨부파일입니다. 게시글에 파일을 여러 개 붙일 수 있으므로 `PostAttachment` 같은 별도 엔티티를 만드는 방식이 괜찮아 보입니다.

첨부파일을 구현할 때는 파일 자체를 DB에 저장하기보다는, 파일은 서버 디스크나 S3 같은 스토리지에 저장하고 DB에는 원본 파일명, 저장 파일명, 경로 또는 URL, 파일 크기 같은 메타데이터를 저장하는 방식을 먼저 적용해 볼 생각입니다.

<!-- '첨부파일 기능을 추가할 예정인 게시글 화면 또는 설계 메모 사진' -->

## 마무리

이번에 댓글과 좋아요를 추가하면서 단순히 API만 만드는 것보다, 데이터가 어떤 관계인지 먼저 생각하는 것이 중요하다는 점을 느꼈습니다. 댓글은 독립적으로 관리할 정보가 많아서 엔티티로 만들었고, 좋아요는 회원과 게시글을 연결하는 관계 테이블로 만들었습니다.

아직 부족한 부분도 많지만, 다음 첨부파일 기능까지 추가하면서 게시판을 조금 더 실제 서비스처럼 만들어 보고 싶습니다.
