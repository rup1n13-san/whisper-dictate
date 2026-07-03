package dev.rup1n13.whisperdictate

import dev.rup1n13.whisperdictate.data.Provisioning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProvisioningTest {

    @Test
    fun `valid payload parses`() {
        val p = Provisioning.parse(
            """{"api_base_url": "https://api.groq.com/openai/v1/", "api_key": "gsk_abc"}"""
        )
        assertEquals("https://api.groq.com/openai/v1", p?.baseUrl)
        assertEquals("gsk_abc", p?.apiKey)
    }

    @Test
    fun `garbage returns null instead of throwing`() {
        assertNull(Provisioning.parse(null))
        assertNull(Provisioning.parse(""))
        assertNull(Provisioning.parse("not json"))
        assertNull(Provisioning.parse("""{"api_key": "gsk_abc"}"""))
        assertNull(Provisioning.parse("""{"api_base_url": "ftp://x", "api_key": "k"}"""))
        assertNull(Provisioning.parse("""{"api_base_url": "https://x", "api_key": ""}"""))
    }
}
