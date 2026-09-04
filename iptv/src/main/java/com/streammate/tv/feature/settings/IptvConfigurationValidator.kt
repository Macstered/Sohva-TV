package com.streammate.tv.feature.settings

import com.streammate.tv.core.model.IptvConfiguration
import com.streammate.tv.core.network.IptvSourceUrlPolicy

object IptvConfigurationValidator {
    fun validate(m3uUrl: String, xmlTvUrl: String): Result<IptvConfiguration> = runCatching {
        IptvConfiguration(
            m3uUrl = validateM3uUrl(m3uUrl),
            xmlTvUrl = requireNotNull(validateOptionalXmlTvUrl(xmlTvUrl)),
        )
    }

    fun validateM3uUrl(value: String): String = IptvSourceUrlPolicy.normalize(value, "M3U")

    fun validateOptionalXmlTvUrl(value: String): String? = value.trim()
        .takeIf(String::isNotBlank)
        ?.let { IptvSourceUrlPolicy.normalize(it, "XMLTV") }
}
