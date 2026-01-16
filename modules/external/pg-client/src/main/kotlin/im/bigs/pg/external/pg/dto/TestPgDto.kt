package im.bigs.pg.external.pg.dto

/**
 * TestPG API 요청 DTO.
 * 암호화된 결제 정보를 담습니다.
 */
data class TestPgRequest(
    val enc: String,
)

/**
 * TestPG API 성공 응답 DTO.
 */
data class TestPgSuccessResponse(
    val approvalCode: String,
    val approvedAt: String,
    val maskedCardLast4: String,
    val amount: Long,
    val status: String,
)

/**
 * TestPG API 실패 응답 DTO.
 */
data class TestPgErrorResponse(
    val code: Int,
    val errorCode: String,
    val message: String,
    val referenceId: String,
)

/**
 * 암호화 전 평문 결제 정보.
 * JSON 직렬화 후 AES-256-GCM으로 암호화됩니다.
 */
data class TestPgPayload(
    val cardNumber: String,
    val birthDate: String,
    val expiry: String,
    val password: String,
    val amount: Long,
)
