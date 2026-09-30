package com.iyftv.app.data.iyf

import java.net.URLDecoder
import java.security.MessageDigest

/** Keys the web app embeds in its page (`pConfig`) to sign API calls. */
data class IyfKeys(val publicKey: String, val privateKeys: List<String>)

/**
 * Signs API queries the way the iyf.tv web client does (`uriSignature` in its
 * main bundle): `vv = md5(publicKey & lowercase(decodedQuery) & privateKey[0])`,
 * sent along with `pub = publicKey`.
 */
object IyfSigner {

    fun sign(query: String, keys: IyfKeys): String {
        val vv = md5("${keys.publicKey}&${decodeQuery(query).lowercase()}&${keys.privateKeys.firstOrNull().orEmpty()}")
        return "$query&vv=$vv&pub=${keys.publicKey}"
    }

    /** Same as the web client's `get_query`: values URL-decoded, `+` as space. */
    fun decodeQuery(query: String): String =
        query.split("&").joinToString("&") { part ->
            val i = part.indexOf('=')
            if (i < 0) part
            else part.substring(0, i) + "=" + URLDecoder.decode(part.substring(i + 1), "UTF-8")
        }

    /**
     * Pulls the keys out of the homepage HTML, which inlines them as
     * `"pConfig":{"publicKey":"…","privateKey":["…"]}`. The page has another,
     * unrelated `publicKey` (Cloudflare), so only the `pConfig` block is read.
     */
    fun parseKeys(html: String): IyfKeys? {
        // Braces and brackets are all escaped: Android's ICU regex engine rejects a
        // bare `}` or `]` that the desktop JVM accepts.
        val block = Regex("\"pConfig\"\\s*:\\s*\\{([^\\}]*)\\}").find(html)?.groupValues?.get(1) ?: return null
        val pub = Regex("\"publicKey\"\\s*:\\s*\"([^\"]+)\"").find(block)?.groupValues?.get(1) ?: return null
        val privs = Regex("\"privateKey\"\\s*:\\s*\\[([^\\]]*)\\]").find(block)?.groupValues?.get(1)
            ?.let { Regex("\"([^\"]+)\"").findAll(it).map { m -> m.groupValues[1] }.toList() }
            ?: Regex("\"privateKey\"\\s*:\\s*\"([^\"]+)\"").find(block)?.groupValues?.get(1)?.let(::listOf)
            ?: emptyList()
        return IyfKeys(pub, privs)
    }

    fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
