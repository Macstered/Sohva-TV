package com.sohva.tv.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest.kotlin
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.detector.api.Issue
import org.junit.Test

class DetectorsTest {
    private val dispatchersStub: TestFile = kotlin(
        """
        package kotlinx.coroutines
        object Dispatchers {
            val IO: Any = Any()
            val Default: Any = Any()
            val Main: Any = Any()
        }
        """,
    ).indented()

    private val animationStub: TestFile = kotlin(
        """
        package androidx.compose.animation.core
        fun rememberInfiniteTransition(): Any = Any()
        fun infiniteRepeatable(): Any = Any()
        """,
    ).indented()

    private val modifierStub: TestFile = kotlin(
        """
        package androidx.compose.ui
        interface Modifier { companion object : Modifier }
        fun Modifier.clip(shape: Any): Modifier = this
        fun Modifier.padding(dp: Int): Modifier = this
        """,
    ).indented()

    private val backgroundStub: TestFile = kotlin(
        """
        package androidx.compose.foundation
        import androidx.compose.ui.Modifier
        fun Modifier.background(color: Long): Modifier = this
        """,
    ).indented()

    private fun run(issue: Issue, vararg files: TestFile) = lint().files(*files).issues(issue).allowMissingSdk().run()

    @Test
    fun dispatchersAreNamedOnlyInTheDispatcherFile() {
        run(
            DispatchersDetector.ISSUE,
            dispatchersStub,
            kotlin(
                """
                package com.sohva.tv.feature
                import kotlinx.coroutines.Dispatchers
                val io = Dispatchers.IO
                val main = Dispatchers.Main
                """,
            ).indented(),
        ).expectErrorCount(1).expectContains("Use AppDispatchers instead of naming a Dispatchers member")

        run(
            DispatchersDetector.ISSUE,
            dispatchersStub,
            kotlin("src/com/sohva/tv/app/AndroidDispatchers.kt", "package com.sohva.tv.app\nimport kotlinx.coroutines.Dispatchers\nval d = Dispatchers.Default\n"),
        ).expectClean()
    }

    @Test
    fun infiniteAnimationsOnlyInTheMotionTokens() {
        run(
            InfiniteAnimationDetector.ISSUE,
            animationStub,
            kotlin("package com.sohva.tv.feature.live\nimport androidx.compose.animation.core.rememberInfiniteTransition\nval t = rememberInfiniteTransition()\n"),
        ).expectErrorCount(1)

        run(
            InfiniteAnimationDetector.ISSUE,
            animationStub,
            kotlin("package com.sohva.tv.ui.design.motion\nimport androidx.compose.animation.core.infiniteRepeatable\nval t = infiniteRepeatable()\n"),
        ).expectClean()
    }

    @Test
    fun offsetPagingIsRefused() {
        run(
            SqlOffsetDetector.ISSUE,
            kotlin(
                """
                package com.sohva.tv.core.data
                const val BAD = "SELECT id FROM film ORDER BY sort_key LIMIT :size OFFSET :start"
                const val ALSO_BAD = "SELECT id FROM film LIMIT 20, 20"
                const val GOOD = "SELECT id FROM film WHERE sort_key > :last ORDER BY sort_key LIMIT :size"
                """,
            ).indented(),
        ).expectErrorCount(2)
    }

    @Test
    fun clipFollowedByBackgroundIsRefused() {
        run(
            ClipBackgroundDetector.ISSUE,
            modifierStub,
            backgroundStub,
            kotlin(
                """
                package com.sohva.tv.feature
                import androidx.compose.foundation.background
                import androidx.compose.ui.Modifier
                import androidx.compose.ui.clip
                import androidx.compose.ui.padding
                val bad = Modifier.clip(8).background(0xFF000000)
                val good = Modifier.padding(4).background(0xFF000000)
                """,
            ).indented(),
        ).expectErrorCount(1)
    }
}
