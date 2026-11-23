package com.example.data

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

data class TvChannel(
    val id: String,
    val tvgId: String,
    val name: String,
    val logo: String,
    val group: String,
    val streamUrl: String,
    val isDash: Boolean,
    val clearKeys: Map<String, String>,
    val headers: Map<String, String>,
    val licenseType: String = "",
    val licenseUrl: String = ""
)

object M3uParser {

    fun fetchPlaylistText(playlistUrl: String): String {
        val conn = (URL(playlistUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) PlusTV/1.0")
            setRequestProperty("Accept", "*/*")
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IOException("HTTP $code while loading playlist")
            }
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun parse(rawText: String): List<TvChannel> {
        val lines = rawText.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        val channels = mutableListOf<TvChannel>()

        var name = ""
        var tvgId = ""
        var logo = ""
        var group = "General"
        var manifestTypeHint: String? = null
        var licenseType = ""
        var licenseKeyRaw = ""
        val headers = LinkedHashMap<String, String>()

        fun reset() {
            name = ""
            tvgId = ""
            logo = ""
            group = "General"
            manifestTypeHint = null
            licenseType = ""
            licenseKeyRaw = ""
            headers.clear()
        }

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line == "#EXTM3U") continue

            when {
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val commaIdx = line.lastIndexOf(',')
                    val meta = if (commaIdx >= 0) line.substring(0, commaIdx) else line
                    val title = if (commaIdx >= 0) line.substring(commaIdx + 1).trim() else ""
                    tvgId = extractAttr(meta, "tvg-id")
                    val tvgName = extractAttr(meta, "tvg-name")
                    logo = extractAttr(meta, "tvg-logo")
                    group = extractAttr(meta, "group-title").ifBlank { "General" }
                    name = title.ifBlank { tvgName.ifBlank { "Channel ${channels.size + 1}" } }
                }

                line.startsWith("#KODIPROP:", ignoreCase = true) -> {
                    val prop = line.substringAfter(':').trim()
                    val eq = prop.indexOf('=')
                    if (eq <= 0) continue
                    val key = prop.substring(0, eq).trim().lowercase()
                    val value = prop.substring(eq + 1).trim()
                    when (key) {
                        "inputstream.adaptive.manifest_type" -> manifestTypeHint = value.lowercase()
                        "inputstream.adaptive.license_type" -> licenseType = value.lowercase()
                        "inputstream.adaptive.license_key" -> licenseKeyRaw = value
                        "inputstream.adaptive.stream_headers" -> {
                            parseHeaderParams(value).forEach { (k, v) ->
                                headers[normalizeHeaderName(k)] = v
                            }
                        }
                    }
                }

                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = line.substringAfter(':').trim()
                    val eq = opt.indexOf('=')
                    if (eq <= 0) continue
                    val key = opt.substring(0, eq).trim().lowercase()
                    val value = opt.substring(eq + 1).trim()
                    if (value.isNotEmpty()) {
                        when (key) {
                            "http-user-agent" -> headers["User-Agent"] = value
                            "http-referrer", "http-referer" -> headers["Referer"] = value
                            "http-origin" -> headers["Origin"] = value
                            "http-cookie" -> headers["Cookie"] = value
                        }
                    }
                }

                line.startsWith("#EXTHTTP:", ignoreCase = true) -> {
                    val jsonStr = line.substringAfter(':').trim()
                    runCatching {
                        val obj = JSONObject(jsonStr)
                        val keys = obj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            val v = obj.optString(k, "").trim()
                            if (v.isNotEmpty()) {
                                headers[normalizeHeaderName(k)] = v
                            }
                        }
                    }
                }

                line.startsWith("#") -> continue

                line.startsWith("http://", ignoreCase = true) ||
                    line.startsWith("https://", ignoreCase = true) -> {
                    val pipeIdx = line.indexOf('|')
                    val rawUrl = if (pipeIdx >= 0) line.substring(0, pipeIdx).trim() else line
                    if (pipeIdx >= 0) {
                        parseHeaderParams(line.substring(pipeIdx + 1)).forEach { (k, v) ->
                            headers[normalizeHeaderName(k)] = v
                        }
                    }

                    val cookie = headers["Cookie"].orEmpty()
                    val hasCookieHdnea = cookie.contains("__hdnea__=")
                    val cleanUrl = if (hasCookieHdnea) stripStaleAuthParams(rawUrl) else rawUrl

                    val trimmedLicense = licenseKeyRaw.trim()
                    val isRemoteLicenseUrl = trimmedLicense.startsWith("http://", ignoreCase = true) ||
                        trimmedLicense.startsWith("https://", ignoreCase = true)

                    val clearKeys = if (trimmedLicense.isNotBlank() && !isRemoteLicenseUrl) {
                        parseClearKeys(trimmedLicense)
                    } else {
                        emptyMap()
                    }

                    val licenseUrl = if (isRemoteLicenseUrl) {
                        trimmedLicense.substringBefore('|').trim()
                    } else {
                        ""
                    }

                    val isDash = manifestTypeHint == "mpd" ||
                        manifestTypeHint == "dash" ||
                        cleanUrl.contains(".mpd", ignoreCase = true)

                    channels.add(
                        TvChannel(
                            id = "ch-${channels.size + 1}-${tvgId.ifBlank { name }}",
                            tvgId = tvgId,
                            name = name.ifBlank { "Channel ${channels.size + 1}" },
                            logo = logo,
                            group = group,
                            streamUrl = cleanUrl,
                            isDash = isDash,
                            clearKeys = clearKeys,
                            headers = LinkedHashMap(headers),
                            licenseType = licenseType,
                            licenseUrl = licenseUrl
                        )
                    )
                    reset()
                }
            }
        }
        return channels
    }

    /**
     * Converts ClearKey hex pairs (kid -> key) into W3C ClearKey JWK JSON bytes
     * required by Media3 LocalMediaDrmCallback.
     */
    fun buildClearKeyJwkBytes(clearKeys: Map<String, String>): ByteArray {
        val keysArr = JSONArray()
        clearKeys.forEach { (kidHex, keyHex) ->
            val kidB64 = hexToBase64Url(kidHex)
            val keyB64 = hexToBase64Url(keyHex)
            if (kidB64.isNotEmpty() && keyB64.isNotEmpty()) {
                keysArr.put(
                    JSONObject().apply {
                        put("kty", "oct")
                        put("kid", kidB64)
                        put("k", keyB64)
                    }
                )
            }
        }
        val root = JSONObject().apply {
            put("keys", keysArr)
            put("type", "temporary")
        }
        return root.toString().toByteArray(Charsets.UTF_8)
    }

    private fun hexToBase64Url(hex: String): String {
        val clean = hex.trim().replace("-", "")
        if (clean.length % 2 != 0 || clean.isEmpty()) return ""
        return runCatching {
            val bytes = ByteArray(clean.length / 2)
            for (i in bytes.indices) {
                bytes[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        }.getOrDefault("")
    }

    private fun parseClearKeys(raw: String): Map<String, String> {
        val trimmed = raw.trim()
        val result = LinkedHashMap<String, String>()
        if (trimmed.startsWith("{")) {
            runCatching {
                val obj = JSONObject(trimmed)
                val arr = obj.optJSONArray("keys")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        val kid = normalizeKeyToHex(item.optString("kid"))
                        val k = normalizeKeyToHex(item.optString("k"))
                        if (kid.isNotEmpty() && k.isNotEmpty()) result[kid] = k
                    }
                } else {
                    // Support flat JSON map format: {"kidHex": "keyHex"}
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val rawKid = keys.next()
                        val rawKey = obj.optString(rawKid, "")
                        val kid = normalizeKeyToHex(rawKid)
                        val k = normalizeKeyToHex(rawKey)
                        if (kid.isNotEmpty() && k.isNotEmpty()) result[kid] = k
                    }
                }
            }
            return result
        }
        trimmed.split(Regex("[\\s,;]+")).forEach { pair ->
            val colon = pair.indexOf(':')
            if (colon > 0) {
                val kid = normalizeKeyToHex(pair.substring(0, colon))
                val key = normalizeKeyToHex(pair.substring(colon + 1))
                if (kid.isNotEmpty() && key.isNotEmpty()) result[kid] = key
            }
        }
        return result
    }

    private fun normalizeKeyToHex(raw: String): String {
        val clean = raw.trim().replace("-", "")
        if (clean.isEmpty()) return ""
        if (clean.length % 2 == 0 && clean.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            return clean.lowercase()
        }
        return base64UrlToHex(raw.trim())
    }

    private fun base64UrlToHex(b64: String): String {
        if (b64.isBlank()) return ""
        return runCatching {
            val bytes = Base64.decode(b64, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            bytes.joinToString("") { "%02x".format(it) }
        }.getOrDefault("")
    }

    private fun parseHeaderParams(raw: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        raw.replace("&amp;", "&", ignoreCase = true)
            .split('&', '|')
            .forEach { part ->
                val eq = part.indexOf('=')
                if (eq > 0) {
                    val k = safeDecode(part.substring(0, eq).trim())
                    val v = safeDecode(part.substring(eq + 1).trim())
                    if (k.isNotEmpty() && v.isNotEmpty()) out[k] = v
                }
            }
        return out
    }

    private fun normalizeHeaderName(name: String): String =
        when (name.trim().lowercase()) {
            "cookie" -> "Cookie"
            "user-agent" -> "User-Agent"
            "referer", "referrer" -> "Referer"
            "origin" -> "Origin"
            "authorization" -> "Authorization"
            "accept" -> "Accept"
            else -> name.trim()
        }

    /**
     * Percent-decodes %XX sequences while preserving literal '+' characters
     * (important for base64 / HMAC auth tokens and User-Agent strings).
     */
    private fun safeDecode(value: String): String {
        if (!value.contains('%')) return value
        return runCatching {
            URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
        }.getOrDefault(value)
    }

    private fun stripStaleAuthParams(url: String): String =
        url.replace(Regex("[?&](__hdnea__|xxx)=[^&]*"), "")
            .replace("?&", "?")
            .removeSuffix("?")

    private fun extractAttr(line: String, attr: String): String {
        val doubleQuoted = Regex("$attr=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
            .find(line)?.groupValues?.getOrNull(1)?.trim()
        if (!doubleQuoted.isNullOrEmpty()) return doubleQuoted
        val singleQuoted = Regex("$attr='([^']*)'", RegexOption.IGNORE_CASE)
            .find(line)?.groupValues?.getOrNull(1)?.trim()
        return singleQuoted.orEmpty()
    }
}
