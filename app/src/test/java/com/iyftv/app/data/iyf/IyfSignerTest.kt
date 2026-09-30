package com.iyftv.app.data.iyf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IyfSignerTest {

    // Shape copied from the live homepage: an unrelated Cloudflare publicKey comes first.
    private val html = """
        "config":[{"apiVersion":"v2","authConfig":{"publicKey":"0x4AAAAAACCL7s","provider":"CLOUDFLARE"},
        "pConfig":{"publicKey":"CJSvC3Sr","privateKey":["SvC3JSvC3"]},"googleMapkey":"x"}]
    """.trimIndent()

    @Test fun parseKeys_readsPConfigNotAuthConfig() {
        assertEquals(IyfKeys("CJSvC3Sr", listOf("SvC3JSvC3")), IyfSigner.parseKeys(html))
    }

    @Test fun parseKeys_nullWithoutPConfig() {
        assertNull(IyfSigner.parseKeys("""{"authConfig":{"publicKey":"0x4"}}"""))
    }

    @Test fun sign_hashesPubLowercasedQueryAndFirstPrivateKey() {
        val keys = IyfKeys("PUB", listOf("k0", "k1"))
        val expected = IyfSigner.md5("PUB&cinema=1&id=ab&k0")
        assertEquals("cinema=1&ID=Ab&vv=$expected&pub=PUB", IyfSigner.sign("cinema=1&ID=Ab", keys))
    }

    @Test fun sign_hashesDecodedValues() {
        val keys = IyfKeys("PUB", listOf("k0"))
        val signed = IyfSigner.sign("tags=%E7%B9%81%E8%8A%B1&page=1", keys)
        assertEquals(IyfSigner.md5("PUB&tags=繁花&page=1&k0"), signed.substringAfter("vv=").substringBefore("&"))
    }
}
