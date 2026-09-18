package io.github.mimai114514.chemeilai.data.remote

import io.github.mimai114514.chemeilai.core.ApiException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

@Serializable
data class Envelope(
    val jsonr: EnvelopeBody? = null,
)

@Serializable
data class EnvelopeBody(
    val status: String? = null,
    val errmsg: String? = null,
    val success: Boolean? = null,
    val data: JsonElement? = null,
)

internal fun Envelope.requireData(): JsonElement {
    val body = jsonr ?: throw ApiException("网络响应为空")
    if (body.status != "00") {
        val message = body.errmsg?.takeIf { it.isNotBlank() } ?: "接口返回错误(${body.status})"
        throw ApiException(message)
    }
    return body.data ?: JsonNull
}
