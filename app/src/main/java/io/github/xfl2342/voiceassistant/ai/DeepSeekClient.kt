package io.github.xfl2342.voiceassistant.ai

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * DeepSeek 对话补全接口的最小实现。
 *
 * 这里刻意只用系统自带的网络与 JSON 能力，不引入第三方库：目前只有一个接口要调，
 * 依赖越少越不容易出问题。等接口变多了再考虑换 OkHttp 也不迟。
 */
class DeepSeekClient(
    private val apiKey: String,
    private val model: String = MODEL_CHAT,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {

    sealed interface Outcome {
        /** 成功拿到模型返回的文本内容。 */
        data class Success(val content: String) : Outcome

        /** 失败，message 可以直接展示给用户。 */
        data class Failure(val message: String, val httpCode: Int? = null) : Outcome
    }

    /**
     * 把一句话解析成行程结构。
     *
     * 注意：这是阻塞调用，必须放在后台线程执行。
     */
    fun parseEvent(userText: String): Outcome {
        val payload = JSONObject().apply {
            put("model", model)
            put("temperature", 0.2)
            put("stream", false)
            // 要求接口直接把结果约束成 json；注意提示词里也必须出现 json 字样。
            put("response_format", JSONObject().put("type", "json_object"))
            put(
                "messages",
                JSONArray().apply {
                    put(message("system", PromptBuilder.systemPrompt()))
                    put(message("user", PromptBuilder.userPrompt(userText)))
                },
            )
        }
        return post(payload)
    }

    private fun message(role: String, content: String): JSONObject =
        JSONObject().apply {
            put("role", role)
            put("content", content)
        }

    private fun post(payload: JSONObject): Outcome {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("$baseUrl/chat/completions").openConnection() as HttpURLConnection)
                .apply {
                    requestMethod = "POST"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }

            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            if (code !in 200..299) {
                return Outcome.Failure(describeHttpError(code, body), code)
            }

            val content = runCatching {
                JSONObject(body)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
            }.getOrDefault("")

            if (content.isBlank()) {
                Outcome.Failure("接口返回了空内容（HTTP $code）", code)
            } else {
                Outcome.Success(content)
            }
        } catch (t: Throwable) {
            Outcome.Failure(t.message ?: t::class.java.simpleName)
        } finally {
            connection?.disconnect()
        }
    }

    private fun describeHttpError(code: Int, body: String): String {
        val detail = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")

        val hint = when (code) {
            401 -> "API Key 无效或已过期"
            402 -> "账户余额不足，需要先充值"
            429 -> "请求过于频繁，稍后再试"
            in 500..599 -> "DeepSeek 服务端出错，稍后再试"
            else -> "请求失败"
        }
        return if (detail.isBlank()) "$hint（HTTP $code）" else "$hint（HTTP $code）：$detail"
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val MODEL_CHAT = "deepseek-chat"
        const val MODEL_REASONER = "deepseek-reasoner"

        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 60_000
    }
}
