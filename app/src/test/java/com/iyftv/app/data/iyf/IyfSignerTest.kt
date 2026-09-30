package com.iyftv.app.data.iyf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IyfSignerTest {

    @Test fun parseKeys_readsInlineConfig() {
        val html = """<script>var injectJson = {"config":[{"pConfig":{"publicKey":"PUB1","privateKey":["k0","k1"]}}]};</script>"""
        assertEquals(IyfKeys("PUB1", listOf("k0", "k1")), IyfSigner.parseKeys(html))
    }

    @Test fun parseKeys_nullWithoutPublicKey() {
        assertEquals(null, IyfSigner.parseKeys("<html></html>"))
    }

    @Test fun sign_appendsVvAndPub() {
        val keys = IyfKeys("PUB", listOf("k0", "k1"))
        val signed = IyfSigner.sign("cinema=1&ID=Ab", keys, nowMillis = 3)
        val expected = IyfSigner.md5("PUB&cinema=1&id=ab&k1")
        assertEquals("cinema=1&ID=Ab&vv=$expected&pub=PUB", signed)
        assertTrue(expected.matches(Regex("[0-9a-f]{32}")))
    }
}
