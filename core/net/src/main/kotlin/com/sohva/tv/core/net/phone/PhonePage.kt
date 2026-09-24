package com.sohva.tv.core.net.phone

import java.security.MessageDigest
import java.util.Base64

/** The Sources page's texts, resolved on the TV in its interface language (spec 11 lesson 9). */
data class PhonePageTexts(
    val languageTag: String,
    val title: String,
    val intro: String,
    val xtream: String,
    val m3u: String,
    val name: String,
    val xtreamUrl: String,
    val username: String,
    val password: String,
    val m3uUrl: String,
    val xmltvUrl: String,
    val keys: String,
    val keysHelp: String,
    val tmdb: String,
    val apiSports: String,
    val send: String,
    val privacy: String,
)

/**
 * The Sources page (spec 11 PHONE-FR-33) with the rebuild's token handling (plan/09 M1): the token
 * travels in the address fragment, which browsers never send; the inline script keeps it in memory,
 * removes it from the address bar, and posts each form with `fetch` and a bearer header. The answer
 * sentence replaces the notice; a reload cannot re-post. No external resources.
 */
object PhonePage {
    private val SCRIPT = """
        var t=location.hash.slice(1);history.replaceState(null,'','/');
        var n=document.getElementById('notice');
        document.querySelectorAll('form').forEach(function(f){f.addEventListener('submit',function(e){
        e.preventDefault();var b=new URLSearchParams(new FormData(f)).toString();
        fetch('/submit',{method:'POST',headers:{'Authorization':'Bearer '+t,'Content-Type':'application/x-www-form-urlencoded'},body:b})
        .then(function(r){return r.text();}).then(function(s){n.textContent=s;n.hidden=false;window.scrollTo(0,0);})
        .catch(function(){n.textContent='…';n.hidden=false;});});});
    """.trimIndent().replace("\n", "")

    private val STYLE = "body{font-family:system-ui,sans-serif;margin:0;padding:20px;background:#12151c;color:#f2f4f8}" +
        "h1{font-size:1.4rem;margin:0 0 4px}h2{font-size:1.1rem;margin:22px 0 8px}p{color:#aab1c0;line-height:1.4}" +
        "form{background:#1c2130;border-radius:12px;padding:14px;margin-top:14px}" +
        "label{display:block;margin:10px 0 4px;color:#aab1c0;font-size:.9rem}" +
        "input{width:100%;box-sizing:border-box;padding:12px;border-radius:8px;border:1px solid #2f3648;background:#0f1218;color:#fff;font-size:1rem}" +
        "button{margin-top:14px;width:100%;padding:14px;border:none;border-radius:10px;background:#ff8a3d;color:#1a0d02;font-weight:bold;font-size:1rem}" +
        ".notice{background:#26304a;color:#fff;padding:12px;border-radius:10px}"

    /** The Content-Security-Policy: only this page's own script and inline style (PHONE-FR-24). */
    val contentSecurityPolicy: String by lazy {
        val hash = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(SCRIPT.toByteArray(Charsets.UTF_8)))
        "default-src 'none'; script-src 'sha256-$hash'; style-src 'unsafe-inline'; connect-src 'self'; " +
            "img-src 'self' blob: data:; form-action 'self'; frame-ancestors 'none'; base-uri 'none'"
    }

    fun sources(texts: PhonePageTexts): String = buildString {
        append("<!DOCTYPE html><html lang=\"").append(escape(texts.languageTag)).append("\"><head><meta charset=\"utf-8\">")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>").append(escape(texts.title))
        append("</title><style>").append(STYLE).append("</style></head><body>")
        append("<h1>").append(escape(texts.title)).append("</h1><p>").append(escape(texts.intro)).append("</p>")
        append("<p id=\"notice\" class=\"notice\" hidden></p>")
        form("xtream", texts.xtream, texts) {
            field(texts.xtreamUrl, "xtream_url", "type=\"url\" required inputmode=\"url\" autocapitalize=\"off\"")
            field(texts.username, "xtream_username", "required autocapitalize=\"off\" autocomplete=\"off\"")
            field(texts.password, "xtream_password", "type=\"password\" required autocomplete=\"off\"")
        }
        form("m3u", texts.m3u, texts) {
            field(texts.m3uUrl, "m3u_url", "type=\"url\" required inputmode=\"url\" autocapitalize=\"off\"")
            field(texts.xmltvUrl, "xmltv_url", "type=\"url\" inputmode=\"url\" autocapitalize=\"off\"")
        }
        append("<form><input type=\"hidden\" name=\"type\" value=\"keys\"><h2>").append(escape(texts.keys)).append("</h2><p>")
        append(escape(texts.keysHelp)).append("</p>")
        field(texts.tmdb, "tmdb_token", "autocapitalize=\"off\" autocomplete=\"off\"")
        field(texts.apiSports, "api_sports_key", "autocapitalize=\"off\" autocomplete=\"off\"")
        append("<button type=\"submit\">").append(escape(texts.send)).append("</button></form>")
        append("<p>").append(escape(texts.privacy)).append("</p><script>").append(SCRIPT).append("</script></body></html>")
    }

    private fun StringBuilder.form(type: String, heading: String, texts: PhonePageTexts, fields: StringBuilder.() -> Unit) {
        append("<form><input type=\"hidden\" name=\"type\" value=\"").append(type).append("\"><h2>").append(escape(heading)).append("</h2>")
        field(texts.name, "name", "required maxlength=\"60\"")
        fields()
        append("<button type=\"submit\">").append(escape(texts.send)).append("</button></form>")
    }

    private fun StringBuilder.field(label: String, name: String, attributes: String) {
        append("<label for=\"").append(name).append("\">").append(escape(label)).append("</label>")
        append("<input id=\"").append(name).append("\" name=\"").append(name).append("\" ").append(attributes).append(">")
    }

    /** `& < > " '` escaped, as every text on the page is (PHONE-FR-33). */
    fun escape(text: String): String = buildString(text.length) {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }
}
