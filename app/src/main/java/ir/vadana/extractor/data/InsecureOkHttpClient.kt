package ir.vadana.extractor.data

import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object InsecureOkHttpClient {

    fun create(): OkHttpClient {
        val trustAllCertificates = object : X509TrustManager {

            override fun checkClientTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?,
            ) {
                // همهٔ گواهی‌های کلاینت پذیرفته می‌شوند.
            }

            override fun checkServerTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?,
            ) {
                // همهٔ گواهی‌های سرور پذیرفته می‌شوند.
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return emptyArray()
            }
        }

        val trustManagers: Array<TrustManager> =
            arrayOf(trustAllCertificates)

        val sslContext = SSLContext.getInstance("TLS").apply {
            init(
                null,
                trustManagers,
                SecureRandom(),
            )
        }

        return OkHttpClient.Builder()
            .sslSocketFactory(
                sslContext.socketFactory,
                trustAllCertificates,
            )
            .hostnameVerifier { _, _ ->
                true
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.MINUTES)
            .writeTimeout(15, TimeUnit.MINUTES)
            .callTimeout(30, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true)
            .build()
    }
}