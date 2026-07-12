package ir.vadana.extractor.data

import ir.vadana.extractor.domain.Recording
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object RecordingUrlParser {
    private val idRegex = Regex("^[A-Za-z0-9_-]+$")
    private val tokenRegex = Regex("^[A-Za-z0-9]*$")
    private val domainRegex = Regex("^[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+$")

    fun parse(raw: String): Recording {
        val trimmed = raw.trim()
        require(trimmed.isNotBlank()) { "لینک کلاس خالی است." }
        val normalized = if (trimmed.matches(Regex("^[A-Za-z][A-Za-z0-9+.-]*://.*"))) {
            trimmed
        } else {
            "https://$trimmed"
        }

        val url = normalized.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("ساختار لینک معتبر نیست.")
        require(url.scheme == "http" || url.scheme == "https") { "فقط HTTP و HTTPS پشتیبانی می‌شود." }
        require(url.username.isEmpty() && url.password.isEmpty()) { "لینک نباید شامل نام کاربری یا رمز باشد." }

        val host = url.host
        require(host != "localhost" && !host.endsWith(".local") &&
            !host.endsWith(".internal") && !host.endsWith(".lan")) {
            "آدرس‌های محلی و داخلی مجاز نیستند."
        }
        if (!host.contains(':') && host.any { it.isLetter() }) {
            require(domainRegex.matches(host)) { "دامنهٔ لینک معتبر نیست." }
        }

        val recordingId = url.pathSegments.lastOrNull { it.isNotBlank() }
            ?: throw IllegalArgumentException("شناسهٔ ضبط در لینک پیدا نشد.")
        val token = url.queryParameter("session").orEmpty()
        require(idRegex.matches(recordingId)) { "شناسهٔ ضبط معتبر نیست." }
        require(tokenRegex.matches(token)) { "توکن session معتبر نیست." }

        val origin = url.newBuilder()
            .encodedPath("/")
            .query(null)
            .fragment(null)
            .build()
            .toString()
            .trimEnd('/')

        return Recording(
            originalUrl = normalized,
            host = origin,
            recordingId = recordingId,
            sessionToken = token,
        )
    }
}
