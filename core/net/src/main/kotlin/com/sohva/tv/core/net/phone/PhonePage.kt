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

/** The Trakt list page's texts (spec 02 HOME-FR-100), in the TV's interface language. */
data class TraktListPageTexts(
    val languageTag: String,
    val title: String,
    val help: String,
    val label: String,
    val send: String,
    val privacy: String,
)

/** The logo page's texts (spec 21 CHAN-FR-41), in the TV's interface language. */
data class LogoPageTexts(
    val languageTag: String,
    val title: String,
    val help: String,
    val choose: String,
    val sending: String,
    val invalid: String,
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

    /**
     * The logo page's script (CHAN-FR-41): the chosen picture is drawn on a canvas at most 512 px
     * on its longer side and posted as a PNG data URL for the channel named in the page. The texts
     * it shows live in the page, so the script stays one constant with one hash.
     */
    private val LOGO_SCRIPT = """
        var t=location.hash.slice(1);history.replaceState(null,'','/');
        var n=document.getElementById('notice'),f=document.getElementById('file'),c=document.getElementById('channel').value;
        var sending=document.getElementById('sending').textContent,invalid=document.getElementById('invalid').textContent;
        f.addEventListener('change',function(){var x=f.files[0];if(!x)return;var u=URL.createObjectURL(x);var i=new Image();
        i.onload=function(){var s=Math.min(1,512/Math.max(i.width,i.height));var w=Math.max(1,Math.round(i.width*s)),h=Math.max(1,Math.round(i.height*s));
        var v=document.createElement('canvas');v.width=w;v.height=h;v.getContext('2d').drawImage(i,0,0,w,h);URL.revokeObjectURL(u);
        n.textContent=sending;n.hidden=false;var b=new URLSearchParams({type:'logo',channel:c,image:v.toDataURL('image/png')}).toString();
        fetch('/submit',{method:'POST',headers:{'Authorization':'Bearer '+t,'Content-Type':'application/x-www-form-urlencoded'},body:b})
        .then(function(r){return r.text();}).then(function(s){n.textContent=s;}).catch(function(){n.textContent='…';});};
        i.onerror=function(){URL.revokeObjectURL(u);n.textContent=invalid;n.hidden=false;};i.src=u;});
    """.trimIndent().replace("\n", "")

    /**
     * The addon page's script (spec 50 FR-46): a chosen .txt file is read on the phone into the
     * masked text area; Send posts the text as `text/plain`; Clear list empties it.
     */
    private val ADDON_SCRIPT = """
        var t=location.hash.slice(1);history.replaceState(null,'','/');
        var n=document.getElementById('notice'),a=document.getElementById('list'),f=document.getElementById('file');
        f.addEventListener('change',function(){var x=f.files[0];if(!x)return;var r=new FileReader();r.onload=function(){a.value=r.result;};r.readAsText(x);});
        document.getElementById('clear').addEventListener('click',function(){a.value='';f.value='';});
        document.getElementById('send').addEventListener('click',function(){
        fetch('/submit',{method:'POST',headers:{'Authorization':'Bearer '+t,'Content-Type':'text/plain; charset=utf-8'},body:a.value})
        .then(function(r){return r.text();}).then(function(s){n.textContent=s;n.hidden=false;}).catch(function(){n.textContent='…';n.hidden=false;});});
    """.trimIndent().replace("\n", "")

    /** The Trakt list page's script (HOME-FR-100): the address goes as `text/plain`; the answer is shown. */
    private val LIST_SCRIPT = """
        var t=location.hash.slice(1);history.replaceState(null,'','/');
        var n=document.getElementById('notice'),a=document.getElementById('list');
        document.getElementById('send').addEventListener('click',function(){
        fetch('/submit',{method:'POST',headers:{'Authorization':'Bearer '+t,'Content-Type':'text/plain; charset=utf-8'},body:a.value})
        .then(function(r){return r.text();}).then(function(s){n.textContent=s;n.hidden=false;}).catch(function(){n.textContent='…';n.hidden=false;});});
    """.trimIndent().replace("\n", "")

    const val ADDONS_SENT: String = "Sent. Review and confirm on your TV."
    const val ADDONS_INVALID: String = "Use a UTF-8 list of 1 to 32 configured addon URLs, up to 256 KiB, one per line."

    private val STYLE = "body{font-family:system-ui,sans-serif;margin:0;padding:20px;background:#12151c;color:#f2f4f8}" +
        "h1{font-size:1.4rem;margin:0 0 4px}h2{font-size:1.1rem;margin:22px 0 8px}p{color:#aab1c0;line-height:1.4}" +
        "form{background:#1c2130;border-radius:12px;padding:14px;margin-top:14px}" +
        "label{display:block;margin:10px 0 4px;color:#aab1c0;font-size:.9rem}" +
        "input{width:100%;box-sizing:border-box;padding:12px;border-radius:8px;border:1px solid #2f3648;background:#0f1218;color:#fff;font-size:1rem}" +
        "button{margin-top:14px;width:100%;padding:14px;border:none;border-radius:10px;background:#ff8a3d;color:#1a0d02;font-weight:bold;font-size:1rem}" +
        ".notice{background:#26304a;color:#fff;padding:12px;border-radius:10px}"

    /** The Content-Security-Policy: only the pages' own two scripts and inline style (PHONE-FR-24). */
    val contentSecurityPolicy: String by lazy {
        fun hash(script: String) = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(script.toByteArray(Charsets.UTF_8)))
        "default-src 'none'; script-src 'sha256-${hash(SCRIPT)}' 'sha256-${hash(LOGO_SCRIPT)}' 'sha256-${hash(ADDON_SCRIPT)}' 'sha256-${hash(LIST_SCRIPT)}'; style-src 'unsafe-inline'; connect-src 'self'; " +
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

    /** One picture for one channel (CHAN-FR-41); [channelKey] rides in a hidden field, escaped. */
    fun logo(texts: LogoPageTexts, channelKey: String): String = buildString {
        append("<!DOCTYPE html><html lang=\"").append(escape(texts.languageTag)).append("\"><head><meta charset=\"utf-8\">")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>").append(escape(texts.title))
        append("</title><style>").append(STYLE).append("</style></head><body>")
        append("<h1>").append(escape(texts.title)).append("</h1><p>").append(escape(texts.help)).append("</p>")
        append("<p id=\"notice\" class=\"notice\" hidden></p>")
        append("<span id=\"sending\" hidden>").append(escape(texts.sending)).append("</span>")
        append("<span id=\"invalid\" hidden>").append(escape(texts.invalid)).append("</span>")
        append("<form><input type=\"hidden\" id=\"channel\" value=\"").append(escape(channelKey)).append("\">")
        append("<label for=\"file\">").append(escape(texts.choose)).append("</label>")
        append("<input id=\"file\" type=\"file\" accept=\"image/*\"></form>")
        append("<script>").append(LOGO_SCRIPT).append("</script></body></html>")
    }

    /** Discover's addon list (spec 50 FR-46), English only: a .txt chooser, a masked list, Send and Clear. */
    fun addons(): String = buildString {
        append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>Sohva addons</title><style>").append(STYLE)
        append("textarea{width:100%;box-sizing:border-box;min-height:160px;padding:12px;border-radius:8px;border:1px solid #2f3648;")
        append("background:#0f1218;color:#fff;-webkit-text-security:disc}</style></head><body>")
        append("<h1>Send addon URLs to your TV</h1>")
        append("<p>Choose a UTF-8 .txt file on this phone or paste configured addon URLs, one per line. Up to 32 addons.</p>")
        append("<p>This local connection is plain HTTP and not encrypted. Use it only on a Wi-Fi network you trust.</p>")
        append("<p id=\"notice\" class=\"notice\" hidden></p><form>")
        append("<label for=\"file\">Text file</label><input id=\"file\" type=\"file\" accept=\".txt,text/plain\">")
        append("<label for=\"list\">Addon URLs</label><textarea id=\"list\" autocapitalize=\"off\" autocomplete=\"off\" spellcheck=\"false\"></textarea>")
        append("<button type=\"button\" id=\"send\">Send to TV</button><button type=\"button\" id=\"clear\">Clear list</button></form>")
        append("<script>").append(ADDON_SCRIPT).append("</script></body></html>")
    }

    /** A Trakt list for Home (HOME-FR-100): one address field and Send. */
    fun traktList(texts: TraktListPageTexts): String = buildString {
        append("<!DOCTYPE html><html lang=\"").append(escape(texts.languageTag)).append("\"><head><meta charset=\"utf-8\">")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>").append(escape(texts.title))
        append("</title><style>").append(STYLE).append("</style></head><body>")
        append("<h1>").append(escape(texts.title)).append("</h1><p>").append(escape(texts.help)).append("</p>")
        append("<p id=\"notice\" class=\"notice\" hidden></p><form>")
        append("<label for=\"list\">").append(escape(texts.label)).append("</label>")
        append("<input id=\"list\" type=\"url\" inputmode=\"url\" autocapitalize=\"off\" autocomplete=\"off\" maxlength=\"300\">")
        append("<button type=\"button\" id=\"send\">").append(escape(texts.send)).append("</button></form>")
        append("<p>").append(escape(texts.privacy)).append("</p><script>").append(LIST_SCRIPT).append("</script></body></html>")
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
