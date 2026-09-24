package com.sohva.tv.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

/** The project's own lint rules; every module's lint runs them (plan/05 §4.11). */
class SohvaIssueRegistry : IssueRegistry() {
    override val issues: List<Issue> = listOf(
        DispatchersDetector.ISSUE,
        InfiniteAnimationDetector.ISSUE,
        SqlOffsetDetector.ISSUE,
        ClipBackgroundDetector.ISSUE,
    )

    override val api: Int = CURRENT_API

    override val vendor: Vendor = Vendor(vendorName = "Sohva TV rebuild", identifier = "sohva-lint")
}
