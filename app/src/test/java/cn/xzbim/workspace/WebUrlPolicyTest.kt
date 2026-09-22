package cn.xzbim.workspace

import cn.xzbim.workspace.security.WebUrlPolicy
import org.junit.Assert.*
import org.junit.Test

class WebUrlPolicyTest {
    @Test fun defaultPortsAndCaseMatch() {
        assertTrue(WebUrlPolicy.sameOrigin("https://EXAMPLE.com/a", "https://example.com:443/b"))
        assertTrue(WebUrlPolicy.sameOrigin("http://example.com", "http://example.com:80"))
    }

    @Test fun differentPortsSchemesAndHostsNeverMatch() {
        assertFalse(WebUrlPolicy.sameOrigin("https://example.com", "http://example.com"))
        assertFalse(WebUrlPolicy.sameOrigin("https://example.com", "https://example.com:8443"))
        assertFalse(WebUrlPolicy.sameOrigin("https://example.com", "https://example.com.evil.test"))
    }

    @Test fun invalidUrlsNeverMatch() {
        assertFalse(WebUrlPolicy.sameOrigin(null, null))
        assertFalse(WebUrlPolicy.sameOrigin("invalid", "invalid"))
        assertFalse(WebUrlPolicy.sameOrigin("file:///tmp/a", "file:///tmp/b"))
        assertFalse(WebUrlPolicy.sameOrigin("https://user:pass@example.com", "https://example.com"))
    }

    @Test fun signInMustBelongToConfiguredServer() {
        assertTrue(WebUrlPolicy.isSignIn("https://example.com/admin/signin/", "https://example.com"))
        assertFalse(WebUrlPolicy.isSignIn("https://other.test/signin", "https://example.com"))
        assertFalse(WebUrlPolicy.isSignIn("https://example.com/signin-fake", "https://example.com"))
    }
}
