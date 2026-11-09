package com.example.mux

import com.example.mux.data.repository.SettingsRepositoryImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {

    @Test
    fun testDefaultSettingsConstants() {
        assertEquals("http://192.168.1.100:8080/api/pushes", SettingsRepositoryImpl.DEFAULT_SERVER_URL)
        assertTrue(SettingsRepositoryImpl.DEFAULT_FORWARDING_ENABLED)
        assertEquals("server_url", SettingsRepositoryImpl.KEY_SERVER_URL.name)
        assertEquals("notification_forwarding_enabled", SettingsRepositoryImpl.KEY_NOTIFICATION_FORWARDING_ENABLED.name)
        assertEquals("api_key", SettingsRepositoryImpl.KEY_API_KEY.name)
    }
}
