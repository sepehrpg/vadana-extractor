package ir.vadana.extractor.data

import android.util.Log
import ir.vadana.extractor.domain.Recording
import ir.vadana.extractor.domain.SharedFile
import ir.vadana.extractor.util.PublicOnlyDns
import ir.vadana.extractor.util.ensureParent
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.max

class ConnectClient(
    private val recording: Recording,
    baseClient: OkHttpClient? = null,
) {
    private val client: OkHttpClient =
        (baseClient ?: InsecureOkHttpClient.create())
            .newBuilder()
            .dns(PublicOnlyDns())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(2, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

    private val origin = recording.host.toHttpUrl()

    fun downloadPackage(
        destination: File,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
        attempts: Int = 3,
    ): File {
        val relative = "/${recording.recordingId}/output/${recording.recordingId}.zip?download=zip"
        var lastError: Throwable? = null
        repeat(attempts) { index ->
            try {
                val part = File(destination.absolutePath + ".part").ensureParent()
                execute(relative).use { response ->
                    if (!response.isSuccessful) {
                        error("دانلود بسته ناموفق بود (HTTP ${response.code}). ممکن است session منقضی شده باشد.")
                    }
                    val body = response.body ?: error("پاسخ سرور خالی است.")
                    val total = body.contentLength().coerceAtLeast(0L)
                    var downloaded = 0L
                    body.byteStream().buffered().use { input ->
                        part.outputStream().buffered().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                onProgress(downloaded, total)
                            }
                        }
                    }
                }
                val header = part.inputStream().use { input -> ByteArray(2).also { input.read(it) } }
                require(header.contentEquals(byteArrayOf('P'.code.toByte(), 'K'.code.toByte()))) {
                    "فایل دریافت‌شده ZIP نیست؛ احتمالاً صفحهٔ ورود یا session منقضی برگشته است."
                }
                if (destination.exists()) destination.delete()
                check(part.renameTo(destination)) { "انتقال فایل دانلودشده انجام نشد." }
                return destination
            } catch (t: Throwable) {
                lastError = t
                File(destination.absolutePath + ".part").delete()
                if (index + 1 < attempts) Thread.sleep(2_000L * (index + 1))
            }
        }

        val reason = buildString {
            append(lastError?.javaClass?.simpleName ?: "UnknownError")

            lastError?.message
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    append(": ")
                    append(it)
                }
        }

        Log.i("ConnectClient",lastError.toString())
        throw IOException(
            "دانلود بسته پس از چند تلاش انجام نشد.\nدلیل: $reason",
            lastError,
        )
        //throw IOException("دانلود بسته پس از چند تلاش انجام نشد.", lastError)
    }

    fun downloadSharedFile(
        item: SharedFile,
        destination: File,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): File? {
        val encodedName = HttpUrl.Builder()
            .scheme("https")
            .host("placeholder.invalid")
            .addPathSegment(item.fileName)
            .build()
            .encodedPathSegments
            .last()
        val separator = if (item.sourceBase.endsWith('/')) "" else "/"
        val relative = "${item.sourceBase}$separator$encodedName?download=true"
        execute(relative).use { response ->
            val contentType = response.header("content-type").orEmpty().lowercase()
            if (!response.isSuccessful || contentType.contains("text/html")) return null
            val body = response.body ?: return null
            val total = max(0L, body.contentLength())
            var downloaded = 0L
            destination.ensureParent().outputStream().buffered().use { output ->
                body.byteStream().buffered().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                }
            }
            if (downloaded <= 0L) {
                destination.delete()
                return null
            }
            return destination
        }
    }

    private fun execute(relative: String): Response {
        var url = resolve(relative)
        repeat(4) {
            val request = Request.Builder()
                .url(withSession(url))
                .header("User-Agent", "Mozilla/5.0 (Android) VadanaExtractor/1.0")
                .apply {
                    if (recording.sessionToken.isNotEmpty()) {
                        header("Cookie", "BREEZESESSION=${recording.sessionToken}")
                    }
                }
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (response.code !in 300..399) return response
            val location = response.header("Location")
            response.close()
            val redirected = location?.let { url.resolve(it) }
                ?: throw IOException("تغییر مسیر نامعتبر از سرور دریافت شد.")
            require(
                redirected.scheme == origin.scheme &&
                    redirected.host == origin.host &&
                    redirected.port == origin.port
            ) {
                "تغییر مسیر به مبدأ دیگر برای جلوگیری از افشای session مسدود شد."
            }
            url = redirected
        }
        throw IOException("تعداد تغییر مسیرهای HTTP بیش از حد مجاز است.")
    }

    private fun resolve(relative: String): HttpUrl = origin.resolve(relative)
        ?: throw IllegalArgumentException("مسیر دانلود معتبر نیست: $relative")

    private fun withSession(url: HttpUrl): HttpUrl {
        if (recording.sessionToken.isEmpty() || url.queryParameter("session") != null) return url
        return url.newBuilder().addQueryParameter("session", recording.sessionToken).build()
    }
}
