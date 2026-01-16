# 변경 이력 (Changelog)

## TestPG 연동

### 추가된 파일

#### 1. AES-256-GCM 암호화 유틸리티
**파일**: `modules/external/pg-client/src/main/kotlin/im/bigs/pg/external/pg/crypto/AesGcmEncryptor.kt`

TestPG API 연동을 위한 암호화 처리를 담당합니다.

- **알고리즘**: AES/GCM/NoPadding
- **키 생성**: API-KEY를 SHA-256 해싱하여 32바이트 키 생성
- **IV**: 12바이트 (96비트), Base64URL 디코딩
- **태그 길이**: 128비트 (16바이트)
- **인코딩**: Base64URL (패딩 없음)

```kotlin
class AesGcmEncryptor(apiKey: String, ivBase64Url: String) {
    fun encrypt(plaintext: String): String  // 암호화 후 Base64URL 인코딩
    fun decrypt(encryptedBase64Url: String): String  // 복호화
}
```

#### 2. TestPG 클라이언트
**파일**: `modules/external/pg-client/src/main/kotlin/im/bigs/pg/external/pg/TestPgClient.kt`

실제 TestPG API를 호출하여 카드 결제를 승인합니다.

- **지원 제휴사**: 짝수 partnerId (MockPgClient는 홀수)
- **암호화**: AES-256-GCM으로 카드 정보 암호화 후 전송
- **에러 처리**: 401 (인증 실패), 422 (결제 거부) 처리

```kotlin
@Component
class TestPgClient(
    private val properties: TestPgProperties,
    private val objectMapper: ObjectMapper,
) : PgClientOutPort {
    override fun supports(partnerId: Long): Boolean = partnerId % 2L == 0L
    override fun approve(request: PgApproveRequest): PgApproveResult
}
```

#### 3. 설정 클래스
**파일**: `modules/external/pg-client/src/main/kotlin/im/bigs/pg/external/pg/config/TestPgProperties.kt`

```kotlin
@ConfigurationProperties(prefix = "test-pg")
data class TestPgProperties(
    val baseUrl: String,
    val apiKey: String,
    val iv: String,
)
```

#### 4. DTO 클래스들
**파일**: `modules/external/pg-client/src/main/kotlin/im/bigs/pg/external/pg/dto/TestPgDto.kt`

| 클래스 | 용도 |
|--------|------|
| `TestPgRequest` | API 요청 (enc 필드) |
| `TestPgSuccessResponse` | 성공 응답 (approvalCode, approvedAt 등) |
| `TestPgErrorResponse` | 실패 응답 (code, errorCode, message) |
| `TestPgPayload` | 암호화 전 평문 결제 정보 |

### 수정된 파일

#### 1. application.yml
**파일**: `modules/bootstrap/api-payment-gateway/src/main/resources/application.yml`

TestPG 연동 설정 추가:
```yaml
# TestPG 연동 설정
test-pg:
  base-url: ${TEST_PG_BASE_URL:https://api-test-pg.bigs.im}
  api-key: ${TEST_PG_API_KEY:}
  iv: ${TEST_PG_IV:}
```

### 테스트

#### TestPgClientTest
- 짝수/홀수 partnerId 지원 여부 테스트
- 실제 TestPG API 호출 테스트

---

## 결제 조회 API

### 커밋: `feat: QueryPaymentsService 필터 기반 결제 내역 조회 로직 구현`

- QueryPaymentsService.query() 메서드 구현
- 커서 기반 페이징 (createdAt desc, id desc)
- 필터링 (partnerId, status, from, to)
- 통계 조회 (count, totalAmount, totalNetAmount)

### 커밋: `test: QueryPaymentsService 결제 내역 조회 단위 테스트 추가`

- items와 summary 함께 반환 검증
- 커서 페이징 시 nextCursor 반환 검증
- 통계가 페이징과 무관하게 전체 집합 기준 검증

---

## 수수료 정책

### 커밋: `feat: PaymentService 동적 정책 조회 로직 구현`

- 하드코딩된 수수료율 제거
- FeePolicyOutPort를 통한 동적 정책 조회
- FeeCalculator를 사용한 수수료 계산