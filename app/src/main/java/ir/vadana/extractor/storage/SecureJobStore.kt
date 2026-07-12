package ir.vadana.extractor.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import ir.vadana.extractor.domain.ExtractionRequest
import ir.vadana.extractor.domain.OutputKind
import ir.vadana.extractor.domain.VideoQuality
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureJobStore(private val context: Context) {
    private val jobsDir = File(context.filesDir, "jobs").apply { mkdirs() }

    fun save(request: ExtractionRequest): String {
        val id = UUID.randomUUID().toString()
        val json = JSONObject().apply {
            put("url", request.recordingUrl)
            put("outputs", JSONArray(request.outputKinds.map { it.name }))
            put("quality", request.quality.name)
            put("fps", request.maxFrameRate.toDouble())
        }.toString().toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(json)
        val bytes = byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + encrypted
        File(jobsDir, "$id.job").writeBytes(bytes)
        return id
    }

    fun load(id: String): ExtractionRequest {
        require(id.matches(Regex("^[0-9a-fA-F-]{36}$"))) { "شناسهٔ کار معتبر نیست." }
        val bytes = File(jobsDir, "$id.job").readBytes()
        val ivLength = bytes.first().toInt() and 0xff
        require(ivLength in 12..32 && bytes.size > ivLength + 1) { "فایل کار رمزگذاری‌شده معتبر نیست." }
        val iv = bytes.copyOfRange(1, 1 + ivLength)
        val encrypted = bytes.copyOfRange(1 + ivLength, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        val json = JSONObject(cipher.doFinal(encrypted).toString(Charsets.UTF_8))
        val array = json.getJSONArray("outputs")
        val outputs = buildSet {
            repeat(array.length()) { add(OutputKind.valueOf(array.getString(it))) }
        }
        return ExtractionRequest(
            recordingUrl = json.getString("url"),
            outputKinds = outputs,
            quality = VideoQuality.valueOf(json.getString("quality")),
            maxFrameRate = json.optDouble("fps", 4.0).toFloat(),
        )
    }

    fun delete(id: String) {
        File(jobsDir, "$id.job").delete()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "vadana_job_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
