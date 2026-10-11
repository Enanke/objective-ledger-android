package com.enanke.awardledger.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyConfigValidatorTest {

    @Test
    fun `blank url is valid demo mode`() {
        assertTrue(ProxyConfigValidator.isValidBaseUrl(""))
        assertTrue(ProxyConfigValidator.isValidBaseUrl("   "))
    }

    @Test
    fun `https root host is valid`() {
        assertTrue(ProxyConfigValidator.isValidBaseUrl("https://proxy.example.com"))
        assertTrue(ProxyConfigValidator.isValidBaseUrl("https://proxy.example.com/"))
        assertTrue(ProxyConfigValidator.isValidBaseUrl("https://proxy.example.com:8787"))
    }

    @Test
    fun `loopback http root is valid for local dev`() {
        assertTrue(ProxyConfigValidator.isValidBaseUrl("http://10.0.2.2:8787"))
        assertTrue(ProxyConfigValidator.isValidBaseUrl("http://127.0.0.1:8787"))
        assertTrue(ProxyConfigValidator.isValidBaseUrl("http://localhost:8787"))
    }

    @Test
    fun `non root path is rejected to avoid duplicated completions path`() {
        assertFalse(ProxyConfigValidator.isValidBaseUrl("https://proxy.example.com/v1/chat/completions"))
        assertFalse(ProxyConfigValidator.isValidBaseUrl("https://proxy.example.com/v1"))
    }

    @Test
    fun `query fragment and userinfo are rejected`() {
        assertFalse(ProxyConfigValidator.isValidBaseUrl("https://proxy.example.com?a=1"))
        assertFalse(ProxyConfigValidator.isValidBaseUrl("https://proxy.example.com#frag"))
        assertFalse(ProxyConfigValidator.isValidBaseUrl("https://user:pass@proxy.example.com"))
    }

    @Test
    fun `non loopback http is rejected`() {
        assertFalse(ProxyConfigValidator.isValidBaseUrl("http://proxy.example.com"))
        assertFalse(ProxyConfigValidator.isValidBaseUrl("http://example.com"))
    }

    @Test
    fun `garbage and missing host are rejected`() {
        assertFalse(ProxyConfigValidator.isValidBaseUrl("not a url"))
        assertFalse(ProxyConfigValidator.isValidBaseUrl("https://"))
        assertFalse(ProxyConfigValidator.isValidBaseUrl("ftp://example.com"))
    }
}
