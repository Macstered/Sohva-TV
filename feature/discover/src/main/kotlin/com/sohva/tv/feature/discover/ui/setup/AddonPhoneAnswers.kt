package com.sohva.tv.feature.discover.ui.setup

import com.sohva.tv.core.net.phone.LogoPageTexts
import com.sohva.tv.core.net.phone.PhoneAnswers
import com.sohva.tv.core.net.phone.PhonePageTexts

/**
 * The addon phone page answers in English, as its page is English only (spec 50 FR-46). Only the
 * refusal sentences are used in addon mode; the Sources and logo pages never open from here.
 */
internal object AddonPhoneAnswers : PhoneAnswers {
    override fun page(): PhonePageTexts = error("The addon phone page has no Sources forms")

    override fun saved(sourceName: String): String = failed()

    override fun keysSaved(): String = failed()

    override fun invalid(): String = "Use a UTF-8 list of 1 to 32 configured addon URLs, up to 256 KiB, one per line."

    override fun failed(): String = "The TV could not take this list. Try again."

    override fun forbidden(): String = "This page is no longer valid. Scan the code on the TV again."

    override fun badRequest(): String = "The request could not be read."

    override fun logoPage(channelName: String): LogoPageTexts = error("The addon phone page has no logo form")

    override fun logoSaved(channelName: String): String = failed()
}
