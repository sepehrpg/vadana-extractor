package ir.vadana.extractor.data

import ir.vadana.extractor.domain.Recording
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object RecordingUrlParser {
    private val idRegex = Regex("^[A-Za-z0-9_-]+$")
    private val tokenRegex = Regex("^[A-Za-z0-9]*$")
    private val domainRegex = Regex("^[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+$")

    fun parse(raw: String): Recording {
        val trimmed = raw.trim()
        require(trimmed.isNotBlank()) { "Class link is empty." }
        val normalized = if (trimmed.matches(Regex("^[A-Za-z][A-Za-z0-9+.-]*://.*"))) {
            trimmed
        } else {
            "https://$trimmed"
        }

        val url = normalized.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("The link structure is invalid.")
        require(url.scheme == "http" || url.scheme == "https") { "Only HTTP and HTTPS are supported." }
        require(url.username.isEmpty() && url.password.isEmpty()) { "The link must not include a username or password." }

        val host = url.host
        require(host != "localhost" && !host.endsWith(".local") &&
            !host.endsWith(".internal") && !host.endsWith(".lan")) {
            "Local and private addresses are not allowed."
        }
        if (!host.contains(':') && host.any { it.isLetter() }) {
            require(domainRegex.matches(host)) { "The link domain is invalid." }
        }

        val recordingId = url.pathSegments.lastOrNull { it.isNotBlank() }
            ?: throw IllegalArgumentException("The recording ID was not found in the link.")
        val token = url.queryParameter("session").orEmpty()
        require(idRegex.matches(recordingId)) { "The recording ID is invalid." }
        require(tokenRegex.matches(token)) { "The session token is invalid." }

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
