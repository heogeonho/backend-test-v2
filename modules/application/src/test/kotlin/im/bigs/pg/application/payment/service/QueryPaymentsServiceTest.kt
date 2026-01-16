package im.bigs.pg.application.payment.service

import im.bigs.pg.application.payment.port.`in`.QueryFilter
import im.bigs.pg.application.payment.port.out.PaymentOutPort
import im.bigs.pg.application.payment.port.out.PaymentPage
import im.bigs.pg.application.payment.port.out.PaymentSummaryProjection
import im.bigs.pg.domain.payment.Payment
import im.bigs.pg.domain.payment.PaymentStatus
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * QueryPaymentsService 단위 테스트.
 * - 결제 내역 조회 시 items와 통계를 함께 반환하는지 검증
 * - 통계는 전체 필터 집합을 대상으로 계산되는지 검증
 */
class QueryPaymentsServiceTest {

    private val paymentRepository = mockk<PaymentOutPort>()
    private val service = QueryPaymentsService(paymentRepository)

    @Test
    @DisplayName("결제 내역 조회 시 items와 summary를 함께 반환해야 한다")
    fun `결제 내역 조회 시 items와 summary를 함께 반환해야 한다`() {
        // Given - 3건의 결제가 있음
        val payment1 = createPayment(id = 1L, amount = BigDecimal("10000"))
        val payment2 = createPayment(id = 2L, amount = BigDecimal("20000"))
        val payment3 = createPayment(id = 3L, amount = BigDecimal("30000"))

        every { paymentRepository.findBy(any()) } returns PaymentPage(
            items = listOf(payment3, payment2, payment1),
            hasNext = false,
            nextCursorCreatedAt = null,
            nextCursorId = null,
        )
        every { paymentRepository.summary(any()) } returns PaymentSummaryProjection(
            count = 3,
            totalAmount = BigDecimal("60000"),
            totalNetAmount = BigDecimal("58590"),
        )

        // When - 결제 내역 조회
        val result = service.query(QueryFilter(limit = 10))

        // Then - items 반환
        assertEquals(3, result.items.size)
        assertEquals(3L, result.items[0].id)
        assertEquals(2L, result.items[1].id)
        assertEquals(1L, result.items[2].id)

        // Then - summary 반환
        assertEquals(3L, result.summary.count)
        assertEquals(BigDecimal("60000"), result.summary.totalAmount)
        assertEquals(BigDecimal("58590"), result.summary.totalNetAmount)

        // Then - 페이징 정보
        assertNull(result.nextCursor)
        assertFalse(result.hasNext)
    }

    @Test
    @DisplayName("커서 페이징 시 다음 페이지가 있으면 nextCursor를 반환해야 한다")
    fun `커서 페이징 시 다음 페이지가 있으면 nextCursor를 반환해야 한다`() {
        // Given - 페이지당 2건씩, 총 5건의 결제가 있음
        val payment1 = createPayment(id = 1L)
        val payment2 = createPayment(id = 2L)

        every { paymentRepository.findBy(any()) } returns PaymentPage(
            items = listOf(payment2, payment1),
            hasNext = true,
            nextCursorCreatedAt = LocalDateTime.of(2026, 1, 14, 12, 0),
            nextCursorId = 1L,
        )
        every { paymentRepository.summary(any()) } returns PaymentSummaryProjection(
            count = 5,
            totalAmount = BigDecimal("50000"),
            totalNetAmount = BigDecimal("48825"),
        )

        // When - 첫 페이지 조회 (limit=2)
        val result = service.query(QueryFilter(limit = 2))

        // Then - 첫 페이지 items만 반환 (2건)
        assertEquals(2, result.items.size)

        // Then - 하지만 summary는 전체 집합 통계 (5건)
        assertEquals(5L, result.summary.count)
        assertEquals(BigDecimal("50000"), result.summary.totalAmount)

        // Then - 다음 페이지 존재
        assertTrue(result.hasNext)
        assertNotNull(result.nextCursor)
    }

    @Test
    @DisplayName("통계는 페이징과 무관하게 전체 필터 집합을 대상으로 계산되어야 한다")
    fun `통계는 페이징과 무관하게 전체 필터 집합을 대상으로 계산되어야 한다`() {
        // Given - 첫 페이지 (2건만 반환)
        every { paymentRepository.findBy(any()) } returns PaymentPage(
            items = listOf(
                createPayment(id = 10L, amount = BigDecimal("10000")),
                createPayment(id = 9L, amount = BigDecimal("20000")),
            ),
            hasNext = true,
            nextCursorCreatedAt = LocalDateTime.of(2026, 1, 14, 12, 0),
            nextCursorId = 9L,
        )
        // Given - 전체 통계 (10건)
        every { paymentRepository.summary(any()) } returns PaymentSummaryProjection(
            count = 10,
            totalAmount = BigDecimal("100000"),
            totalNetAmount = BigDecimal("97650"),
        )

        // When - 첫 페이지 조회
        val firstPage = service.query(QueryFilter(limit = 2))

        // Then - items는 2건만
        assertEquals(2, firstPage.items.size)
        assertEquals(BigDecimal("10000"), firstPage.items[0].amount)
        assertEquals(BigDecimal("20000"), firstPage.items[1].amount)

        // Then - summary는 전체 10건 기준
        assertEquals(10L, firstPage.summary.count)
        assertEquals(BigDecimal("100000"), firstPage.summary.totalAmount)
        assertEquals(BigDecimal("97650"), firstPage.summary.totalNetAmount)

        // Given - 두 번째 페이지 (2건만 반환)
        every { paymentRepository.findBy(any()) } returns PaymentPage(
            items = listOf(
                createPayment(id = 8L, amount = BigDecimal("30000")),
                createPayment(id = 7L, amount = BigDecimal("40000")),
            ),
            hasNext = true,
            nextCursorCreatedAt = LocalDateTime.of(2026, 1, 14, 11, 0),
            nextCursorId = 7L,
        )
        // Given - 통계는 동일 (전체 10건)
        every { paymentRepository.summary(any()) } returns PaymentSummaryProjection(
            count = 10,
            totalAmount = BigDecimal("100000"),
            totalNetAmount = BigDecimal("97650"),
        )

        // When - 두 번째 페이지 조회 (커서 사용)
        val secondPage = service.query(
            QueryFilter(
                cursor = firstPage.nextCursor,
                limit = 2,
            ),
        )

        // Then - items는 다른 2건
        assertEquals(2, secondPage.items.size)
        assertEquals(BigDecimal("30000"), secondPage.items[0].amount)
        assertEquals(BigDecimal("40000"), secondPage.items[1].amount)

        // Then - summary는 첫 페이지와 동일해야 함! (핵심 검증)
        assertEquals(firstPage.summary.count, secondPage.summary.count)
        assertEquals(firstPage.summary.totalAmount, secondPage.summary.totalAmount)
        assertEquals(firstPage.summary.totalNetAmount, secondPage.summary.totalNetAmount)
    }

    private fun createPayment(
        id: Long = 1L,
        partnerId: Long = 1L,
        amount: BigDecimal = BigDecimal("10000"),
    ) = Payment(
        id = id,
        partnerId = partnerId,
        amount = amount,
        appliedFeeRate = BigDecimal("0.0235"),
        feeAmount = amount.multiply(BigDecimal("0.0235")),
        netAmount = amount.subtract(amount.multiply(BigDecimal("0.0235"))),
        cardBin = "123456",
        cardLast4 = "7890",
        approvalCode = "APPROVAL-$id",
        approvedAt = LocalDateTime.of(2026, 1, 14, 12, 0),
        status = PaymentStatus.APPROVED,
        createdAt = LocalDateTime.of(2026, 1, 14, 12, 0),
        updatedAt = LocalDateTime.of(2026, 1, 14, 12, 0),
    )
}
