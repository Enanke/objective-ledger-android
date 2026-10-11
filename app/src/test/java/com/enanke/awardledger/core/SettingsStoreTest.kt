package com.enanke.awardledger.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsStoreTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `proxy base url and token round trip`() {
        val store = SettingsStore(context)
        store.setProxyBaseUrl("https://proxy.example.com")
        store.setProxyAccessToken("secret-token")

        assertEquals("https://proxy.example.com", store.proxyBaseUrl())
        assertEquals("secret-token", store.proxyAccessToken())
        assertFalse(store.isDemoMode())
    }

    @Test
    fun `blank url is demo mode`() {
        val store = SettingsStore(context)
        store.setProxyBaseUrl("")
        assertTrue(store.isDemoMode())
    }

    @Test
    fun `token trimmed on save`() {
        val store = SettingsStore(context)
        store.setProxyAccessToken("  secret-token  ")
        assertEquals("secret-token", store.proxyAccessToken())
    }
}
