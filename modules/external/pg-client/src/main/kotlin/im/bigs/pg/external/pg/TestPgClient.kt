package im.bigs.pg.external.pg

import com.fasterxml.jackson.databind.ObjectMapper
import im.bigs.pg.application.pg.port.out.PgApproveRequest
import im.bigs.pg.application.pg.port.out.PgApproveResult
import im.bigs.pg.application.pg.port.out.PgClientOutPort
import im.bigs.pg.domain.payment.PaymentStatus
import im.bigs.pg.external.pg.config.TestPgProperties
import im.bigs.pg.external.pg.crypto.AesGcmEncryptor
import im.bigs.pg.external.pg.dto.TestPgErrorResponse
import im.bigs.pg.external.pg.dto.TestPgPayload
import im.bigs.pg.external.pg.dto.TestPgRequest
import im.bigs.pg.external.pg.dto.TestPgSuccessResponse
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * TestPG 클라이언트.
 *
 * 실제 TestPG API를 호출하여 카드 결제를 승인합니다.
 * - 짝수 partnerId를 지원합니다 (MockPgClient는 홀수)
 * - AES-256-GCM으로 카드 정보를 암호화하여 전송합니다
 */
@Component
@EnableConfigurationProperties(TestPgProperties::class)
class TestPgClient(
    private val properties: TestPgProperties,
    private val objectMapper: ObjectMapper,
    private val testPgRestTemplate: RestTemplate,
) : PgClientOutPort {

    private val log = LoggerFactory.getLogger(javaClass)

    private val encryptor: AesGcmEncryptor by lazy {
        AesGcmEncryptor(properties.apiKey, properties.iv)
    }

    override fun supports(partnerId: Long): Boolean = partnerId % 2L == 0L

    override fun approve(request: PgApproveRequest): PgApproveResult {
        log.info("TestPG 결제 요청: partnerId={}, amount={}", request.partnerId, request.amount)

        val encryptedPayload = encryptPayload(request)
        val httpRequest = createHttpRequest(encryptedPayload)

        return executeRequest(httpRequest)
    }

    private fun encryptPayload(request: PgApproveRequest): String {
        val payload = TestPgPayload(
            cardNumber = TEST_CARD_NUMBER,
            birthDate = TEST_BIRTH_DATE,
            expiry = TEST_EXPIRY,
            password = TEST_PASSWORD,
            amount = request.amount.toLong(),
        )
        val json = objectMapper.writeValueAsString(payload)
        return encryptor.encrypt(json)
    }

    private fun createHttpRequest(encryptedPayload: String): HttpEntity<TestPgRequest> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set(HEADER_API_KEY, properties.apiKey)
        }
        return HttpEntity(TestPgRequest(enc = encryptedPayload), headers)
    }

    private fun executeRequest(httpRequest: HttpEntity<TestPgRequest>): PgApproveResult {
        return try {
            val response = testPgRestTemplate.postForEntity(
                "${properties.baseUrl}$API_ENDPOINT",
                httpRequest,
                TestPgSuccessResponse::class.java,
            )
            handleSuccess(response.body)
        } catch (e: HttpClientErrorException) {
            handleError(e)
        }
    }

    private fun handleSuccess(body: TestPgSuccessResponse?): PgApproveResult {
        requireNotNull(body) { "TestPG 응답 본문이 비어있습니다" }

        log.info("TestPG 결제 성공: approvalCode={}", body.approvalCode)

        return PgApproveResult(
            approvalCode = body.approvalCode,
            approvedAt = LocalDateTime.parse(body.approvedAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            status = PaymentStatus.valueOf(body.status),
        )
    }

    private fun handleError(e: HttpClientErrorException): Nothing {
        when (e.statusCode) {
            HttpStatus.UNAUTHORIZED -> {
                log.error("TestPG 인증 실패: API-KEY가 유효하지 않습니다")
                throw IllegalStateException("TestPG 인증 실패")
            }
            HttpStatus.UNPROCESSABLE_ENTITY -> handlePaymentRejection(e)
            else -> {
                log.error("TestPG 예상치 못한 에러: status={}, body={}", e.statusCode, e.responseBodyAsString)
                throw IllegalStateException("TestPG 결제 실패: ${e.statusCode}")
            }
        }
    }

    private fun handlePaymentRejection(e: HttpClientErrorException): Nothing {
        val errorResponse = runCatching {
            objectMapper.readValue(e.responseBodyAsString, TestPgErrorResponse::class.java)
        }.getOrElse {
            log.error("TestPG 에러 응답 파싱 실패: {}", e.responseBodyAsString)
            throw IllegalStateException("TestPG 결제 실패: ${e.responseBodyAsString}")
        }

        log.error(
            "TestPG 결제 거부: code={}, errorCode={}, message={}",
            errorResponse.code,
            errorResponse.errorCode,
            errorResponse.message,
        )
        throw IllegalStateException("TestPG 결제 실패: ${errorResponse.message}")
    }

    companion object {
        private const val API_ENDPOINT = "/api/v1/pay/credit-card"
        private const val HEADER_API_KEY = "API-KEY"

        // 테스트용 고정 카드 정보 (성공 카드)
        // 현재 컨트롤러에서 받아오고 있지 않음 -> 문서상 처리안에 대해 명확하지 않아 확인 후 변경 필요
        // 이에 따라 임의로 값 설정하여 호출.
        // TODO: 클라이언트 측에서 받아와야 할 정보 -> 요청 dto 수정 필요
        private const val TEST_CARD_NUMBER = "1111-1111-1111-1111"
        private const val TEST_BIRTH_DATE = "19900101"
        private const val TEST_EXPIRY = "1227"
        private const val TEST_PASSWORD = "12"
    }
}
