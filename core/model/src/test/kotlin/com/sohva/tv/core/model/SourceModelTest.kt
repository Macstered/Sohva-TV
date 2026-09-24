package com.sohva.tv.core.model

import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.AppError.FieldLabel
import com.sohva.tv.core.model.error.AppErrors
import com.sohva.tv.core.model.source.ImportRoute
import com.sohva.tv.core.model.source.Source
import com.sohva.tv.core.model.source.SourceConfig
import com.sohva.tv.core.model.source.SourceRules
import com.sohva.tv.core.model.source.SourceSecrets
import com.sohva.tv.core.model.source.SourceType
import com.sohva.tv.core.model.source.XtreamAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceModelTest {
    private fun m3u(url: String, xmltv: String? = null, name: String = "Home"): SourceConfig =
        SourceConfig(Source("m3u-1", name, SourceType.M3U), SourceSecrets(m3uUrl = url, xmlTvUrl = xmltv))

    private fun xtream(server: String, user: String?, password: String?): SourceConfig = SourceConfig(
        Source("xtream-1", "Panel", SourceType.XTREAM),
        SourceSecrets(xtreamBaseUrl = server, xtreamUsername = user, xtreamPassword = password),
    )

    private fun error(config: SourceConfig): AppError = (SourceRules.validate(config) as SourceRules.Result.Invalid).error
    private fun valid(config: SourceConfig): SourceConfig = (SourceRules.validate(config) as SourceRules.Result.Valid).config

    @Test
    fun addressPolicyAcceptsHttpAndHttpsWithAHostOnly() {
        assertNull(SourceRules.checkAddress("  HTTPS://provider.example/list.m3u ", FieldLabel.M3U))
        assertNull(SourceRules.checkAddress("http://provider.example:8080", FieldLabel.M3U))
        for (bad in listOf("", "provider.example/list.m3u", "ftp://provider.example/x", "http:///nohost", "http://provider example/")) {
            assertEquals(bad, AppError.SourceUrlInvalid(FieldLabel.M3U), SourceRules.checkAddress(bad, FieldLabel.M3U))
        }
    }

    @Test
    fun validationRunsInSpecOrderAndNormalises() {
        assertEquals(AppError.SourceNameRequired, error(m3u("x", name = "  ")))
        assertEquals(AppError.SourceNameTooLong(100), error(m3u("http://provider.example", name = "n".repeat(101))))
        assertEquals(AppError.SourceUrlInvalid(FieldLabel.M3U), error(m3u("nope")))
        assertEquals(AppError.SourceUrlInvalid(FieldLabel.XMLTV), error(m3u("http://provider.example", "epg.xml")))
        assertNull(valid(m3u(" http://provider.example/a ", "  ")).secrets.xmlTvUrl)
        assertEquals("http://provider.example/a", valid(m3u(" http://provider.example/a ")).secrets.m3uUrl)

        assertEquals(AppError.SourceUrlInvalid(FieldLabel.XTREAM_SERVER), error(xtream("panel", "u", "p")))
        assertEquals(AppError.XtreamUsernameMissing, error(xtream("http://panel.example", " ", "p")))
        assertEquals(AppError.XtreamPasswordMissing, error(xtream("http://panel.example", "u", "")))
        val ok = valid(xtream("http://panel.example:8080//", " viewer ", " pa ss "))
        assertEquals("http://panel.example:8080", ok.secrets.xtreamBaseUrl)
        assertEquals("viewer", ok.secrets.xtreamUsername)
        assertEquals(" pa ss ", ok.secrets.xtreamPassword)
    }

    @Test
    fun limitsAndOffsetAreClampedToTheirSteps() {
        val base = m3u("http://provider.example")
        val source = valid(base.copy(source = base.source.copy(connectionLimit = 40, epgOffsetMinutes = 745))).source
        assertEquals(16, source.connectionLimit)
        assertEquals(720, source.epgOffsetMinutes)
        assertTrue(SourceRules.isValidId("m3u-primary"))
        assertFalse(SourceRules.isValidId("bad id"))
        assertTrue(SourceRules.newId(SourceType.XTREAM).startsWith("xtream-"))
    }

    @Test
    fun secretsNeverAppearInTextForms() {
        val config = xtream("http://panel.example", "viewer", "hunter2")
        assertFalse(config.toString().contains("hunter2") || config.toString().contains("panel.example"))
        assertFalse(config.secrets.toString().contains("viewer"))
        assertFalse(XtreamAccount("http://panel.example", "viewer", "hunter2").toString().contains("hunter2"))
    }

    @Test
    fun getPhpAddressesAreImportedThroughXtream() {
        assertEquals(
            XtreamAccount("http://panel.example:8080/sub", "user name", "p&w"),
            ImportRoute.xtreamFromGetPhp("http://panel.example:8080/sub/GET.PHP?USERNAME=user%20name&Password=p%26w&type=m3u_plus"),
        )
        assertNull(ImportRoute.xtreamFromGetPhp("http://panel.example/get.php?username=u"))
        assertNull(ImportRoute.xtreamFromGetPhp("http://panel.example/list.m3u?username=u&password=p"))
        val route = ImportRoute.of(m3u("http://panel.example/get.php?username=u&password=p", "http://epg.example/g.xml"))
        assertTrue(route is ImportRoute.Xtream && route.explicitGuideUrl == "http://epg.example/g.xml")
        assertTrue(ImportRoute.of(m3u("http://provider.example/list.m3u")) is ImportRoute.M3u)
    }

    @Test
    fun storedErrorsRoundTripAndUnknownCodesReadAsUnknown() {
        val errors = listOf(
            AppError.TransportFailed("timeout"), AppError.HttpStatus(404), AppError.SourceUrlInvalid(FieldLabel.XTREAM_SERVER),
            AppError.XtreamHttp(512), AppError.ConnectionLimit("Living room", 2), AppError.EpgUnmatched, AppError.SourceNameTooLong(100),
        )
        errors.forEach { assertEquals(it, AppErrors.restore(it.code, it.args)) }
        assertEquals(AppError.Unknown, AppErrors.restore("code_of_a_newer_build", emptyList()))
        assertEquals(AppError.Unknown, AppErrors.restore("xtream_http", listOf("not a number")))
    }
}
