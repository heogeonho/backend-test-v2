package im.bigs.pg.application.payment.service

import im.bigs.pg.application.partner.port.out.FeePolicyOutPort
import im.bigs.pg.application.partner.port.out.PartnerOutPort
import im.bigs.pg.application.payment.port.`in`.PaymentCommand
import im.bigs.pg.application.payment.port.out.PaymentOutPort
import im.bigs.pg.application.pg.port.out.PgApproveRequest
import im.bigs.pg.application.pg.port.out.PgApproveResult
import im.bigs.pg.application.pg.port.out.PgClientOutPort
import im.bigs.pg.domain.partner.FeePolicy
import im.bigs.pg.domain.partner.Partner
import im.bigs.pg.domain.payment.Payment
import im.bigs.pg.domain.payment.PaymentStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 수수료 정책 적용 통합 테스트.
 */
class FeePolicyIntegrationTest {
    private val partnerRepo = mockk<PartnerOutPort>()
    private val feeRepo = mockk<FeePolicyOutPort>()
    private val paymentRepo = mockk<PaymentOutPort>()

    private val mockPgClient = object : PgClientOutPort {
        override fun supports(partnerId: Long) = true
        override fun approve(request: PgApproveRequest) = PgApproveResult(
            approvalCode = "APPROVAL-${System.currentTimeMillis()}",
            approvedAt = LocalDateTime.now(),
            status = PaymentStatus.APPROVED,
        )
    }

    @Test
    @DisplayName("제휴사별로 다른 수수료 정책이 적용되어야 한다")
    fun `제휴사별로 다른 수수료 정책이 적용되어야 한다`() {
        val service = PaymentService(partnerRepo, feeRepo, paymentRepo, listOf(mockPgClient))

        // Partner 1: 2.35% 만
        every { partnerRepo.findById(1L) } returns Partner(1L, "P1", "Partner 1", true)
        every { feeRepo.findEffectivePolicy(1L, any()) } returns FeePolicy(
            partnerId = 1L,
            effectiveFrom = LocalDateTime.of(2020, 1, 1, 0, 0),
            percentage = BigDecimal("0.0235"),
            fixedFee = null,
        )
        val payment1Slot = slot<Payment>()
        every { paymentRepo.save(capture(payment1Slot)) } answers { payment1Slot.captured.copy(id = 1L) }

        val res1 = service.pay(PaymentCommand(partnerId = 1L, amount = BigDecimal("10000")))

        assertEquals(BigDecimal("235"), res1.feeAmount) // 10000 * 0.0235 = 235
        assertEquals(BigDecimal("9765"), res1.netAmount)

        // Partner 2: 3% + 100원
        every { partnerRepo.findById(2L) } returns Partner(2L, "P2", "Partner 2", true)
        every { feeRepo.findEffectivePolicy(2L, any()) } returns FeePolicy(
            partnerId = 2L,
            effectiveFrom = LocalDateTime.of(2020, 1, 1, 0, 0),
            percentage = BigDecimal("0.0300"),
            fixedFee = BigDecimal("100"),
        )
        val payment2Slot = slot<Payment>()
        every { paymentRepo.save(capture(payment2Slot)) } answers { payment2Slot.captured.copy(id = 2L) }

        val res2 = service.pay(PaymentCommand(partnerId = 2L, amount = BigDecimal("10000")))

        assertEquals(BigDecimal("400"), res2.feeAmount) // (10000 * 0.03) + 100 = 400
        assertEquals(BigDecimal("9600"), res2.netAmount)
    }

    @Test
    @DisplayName("수수료 정책이 없을 경우 예외가 발생해야 한다")
    fun `수수료 정책이 없을 경우 예외가 발생해야 한다`() {
        val service = PaymentService(partnerRepo, feeRepo, paymentRepo, listOf(mockPgClient))

        every { partnerRepo.findById(1L) } returns Partner(1L, "P1", "Partner 1", true)
        every { feeRepo.findEffectivePolicy(1L, any()) } returns null

        val exception = assertFailsWith<IllegalStateException> {
            service.pay(PaymentCommand(partnerId = 1L, amount = BigDecimal("10000")))
        }

        assert(exception.message?.contains("No fee policy found") == true)
    }
}
