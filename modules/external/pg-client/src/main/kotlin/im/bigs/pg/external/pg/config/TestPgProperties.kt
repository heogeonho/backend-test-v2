package im.bigs.pg.external.pg.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * TestPG 연동 설정.
 */
@ConfigurationProperties(prefix = "test-pg")
data class TestPgProperties(
    val baseUrl: String,
    val apiKey: String,
    val iv: String,
)
