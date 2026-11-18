package com.example.updates

/**
 * Parses the small, flat `update.json` emitted by CI.
 *
 * It deliberately avoids org.json: the format is fixed and this way the parser
 * stays pure JVM code that can be covered by the local JUnit tests.
 */
object UpdateJsonParser {

    fun parse(json: String): UpdateInfo? {
        val code = number("versionCode", json) ?: return null
        val name = string("versionName", json) ?: return null
        val tag = string("tag", json) ?: return null
        val apk = string("apk", json) ?: return null
        val sha = string("sha256", json) ?: return null
        if (code <= 0 || code > Int.MAX_VALUE ||
            name.isBlank() || tag.isBlank() || apk.isBlank() || sha.isBlank()
        ) return null

        return UpdateInfo(
            versionCode = code.toInt(),
            versionName = name,
            tag = tag,
            apkName = apk,
            sha256 = sha.lowercase(),
            sizeBytes = number("size", json) ?: 0L,
            releaseUrl = "",
            notes = ""
        )
    }

    private fun number(key: String, json: String): Long? =
        Regex("\"$key\"\\s*:\\s*(-?\\d+)").find(json)?.groupValues?.get(1)?.toLongOrNull()

    private fun string(key: String, json: String): String? =
        Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
            .find(json)
            ?.groupValues
            ?.get(1)
            ?.let(::unescape)

    /** Single pass so `\\n` stays a literal backslash followed by an n. */
    private fun unescape(value: String): String {
        val result = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            if (value[index] != '\\' || index + 1 >= value.length) {
                result.append(value[index++])
                continue
            }
            result.append(
                when (value[index + 1]) {
                    '"' -> '"'
                    '\\' -> '\\'
                    'n' -> '\n'
                    'r' -> '\r'
                    't' -> '\t'
                    else -> value[index + 1]
                }
            )
            index += 2
        }
        return result.toString()
    }
}
