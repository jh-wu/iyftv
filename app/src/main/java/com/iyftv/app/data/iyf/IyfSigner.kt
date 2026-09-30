package com.iyftv.app.data.iyf

import java.security.MessageDigest

/** Keys the web app embeds in its page to sign API calls. */
data class IyfKeys(val publicKey: String, val privateKeys: List<String>)

/**
 * Signs API queries the way the iyf.tv web client does: every call carries
 * `vv` (an MD5 over the public key, the lowercased query and one private key)
 * and `pub` (the public key).
 *
 * NOTE: reconstructed without live access; verify against a real request.
 */
object IyfSigner {

    fun sign(query: String, keys: IyfKeys, nowMillis: Long = System.currentTimeMillis()): String {
        val privateKey = keys.privateKeys.takeIf { it.isNotEmpty() }
            ?.let { it[(nowMillis % it.size).toInt()] }
            .orEmpty()
        val vv = md5("${keys.publicKey}&${query.lowercase()}&$privateKey")
        return "$query&vv=$vv&pub=${keys.publicKey}"
    }

    /**
     * Pulls the keys out of the homepage HTML, which inlines them as
     * `"publicKey":"…"` and `"privateKey":["…", …]` inside a JSON blob.
     */
    fun parseKeys(html: String): IyfKeys? {
        val pub = Regex("\"publicKey\"\\s*:\\s*\"([^\"]+)\"").find(html)?.groupValues?.get(1)
            ?: return null
        val privBlock = Regex("\"privateKey\"\\s*:\\s*\\[([^\\]]*)]").find(html)?.groupValues?.get(1)
        val privs = privBlock
            ?.let { Regex("\"([^\"]+)\"").findAll(it).map { m -> m.groupValues[1] }.toList() }
            ?: Regex("\"privateKey\"\\s*:\\s*\"([^\"]+)\"").find(html)?.groupValues?.get(1)?.let(::listOf)
            ?: emptyList()
        return IyfKeys(pub, privs)
    }

    fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
