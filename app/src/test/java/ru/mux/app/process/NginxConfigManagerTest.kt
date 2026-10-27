package ru.mux.app.process

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.mux.app.nginx.NginxConfig
import ru.mux.app.nginx.NginxConfigManager
import ru.mux.app.nginx.ProxyRule
import java.io.File

class NginxConfigManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var configManager: NginxConfigManager

    @Before
    fun setup() {
        tempFolder.create()
        configManager = NginxConfigManager(tempFolder.root)
    }

    @Test
    fun `initialization creates default nginx conf file and index html`() {
        assertTrue(configManager.configFile.exists())
        assertTrue(configManager.configFile.length() > 0)
        assertTrue(File(configManager.htmlDir, "index.html").exists())
    }

    @Test
    fun `generateConfigText produces valid nginx configuration format`() {
        val config = NginxConfig(
            port = 9090,
            serverName = "myserver.local",
            rootDir = "/tmp/web",
            proxyRules = listOf(ProxyRule(locationPath = "/api", proxyPassUrl = "http://localhost:3000"))
        )

        val text = configManager.generateConfigText(config)

        assertTrue(text.contains("listen 9090;"))
        assertTrue(text.contains("server_name myserver.local;"))
        assertTrue(text.contains("root /tmp/web;"))
        assertTrue(text.contains("location /api {"))
        assertTrue(text.contains("proxy_pass http://localhost:3000;"))
    }

    @Test
    fun `writeConfig and parseConfig correctly roundtrip values`() {
        val original = NginxConfig(
            port = 8081,
            serverName = "test.domain",
            rootDir = tempFolder.root.absolutePath,
            proxyRules = listOf(ProxyRule(locationPath = "/backend", proxyPassUrl = "http://127.0.0.1:5000"))
        )

        configManager.writeConfig(original)
        val parsed = configManager.parseConfig()

        assertEquals(8081, parsed.port)
        assertEquals("test.domain", parsed.serverName)
        assertEquals(tempFolder.root.absolutePath, parsed.rootDir)
        assertEquals(1, parsed.proxyRules.size)
        assertEquals("/backend", parsed.proxyRules.first().locationPath)
        assertEquals("http://127.0.0.1:5000", parsed.proxyRules.first().proxyPassUrl)
    }

    @Test
    fun `updatePort modifies port in config file`() {
        configManager.updatePort(9999)
        val parsed = configManager.parseConfig()
        assertEquals(9999, parsed.port)
    }

    @Test
    fun `updateServerName modifies server name in config file`() {
        configManager.updateServerName("updated.host")
        val parsed = configManager.parseConfig()
        assertEquals("updated.host", parsed.serverName)
    }

    @Test
    fun `addProxyRule appends new proxy rule`() {
        configManager.addProxyRule("/api/v1", "http://localhost:4000")
        val parsed = configManager.parseConfig()
        assertEquals(1, parsed.proxyRules.size)
        assertEquals("/api/v1", parsed.proxyRules.first().locationPath)
        assertEquals("http://localhost:4000", parsed.proxyRules.first().proxyPassUrl)
    }

    @Test
    fun `validateConfig identifies valid and invalid configs`() {
        val validConfig = configManager.readConfig()
        assertTrue(configManager.validateConfig(validConfig))

        val invalidBraces = "http { server { listen 80; }"
        assertFalse(configManager.validateConfig(invalidBraces))

        val missingServer = "http { worker_processes 1; }"
        assertFalse(configManager.validateConfig(missingServer))

        val missingListen = "http { server { server_name localhost; } }"
        assertFalse(configManager.validateConfig(missingListen))
    }
}
