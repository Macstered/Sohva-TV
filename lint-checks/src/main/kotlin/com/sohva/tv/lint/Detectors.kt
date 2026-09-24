package com.sohva.tv.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.getContainingUFile
import org.jetbrains.uast.skipParenthesizedExprDown

private fun issue(id: String, brief: String, explanation: String, detector: Class<out Detector>): Issue = Issue.create(
    id = id,
    briefDescription = brief,
    explanation = explanation,
    category = Category.PERFORMANCE,
    priority = 8,
    severity = Severity.ERROR,
    implementation = Implementation(detector, Scope.JAVA_FILE_SCOPE),
)

/** `Dispatchers.IO` and `Dispatchers.Default` are named only in the app's dispatcher file (plan/03 §4.7). */
class DispatchersDetector : Detector(), SourceCodeScanner {
    override fun getApplicableReferenceNames(): List<String> = listOf("IO", "Default")

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {
        val owner = (referenced as? PsiMember)?.containingClass?.qualifiedName ?: return
        if (owner != "kotlinx.coroutines.Dispatchers") return
        if (context.file.name == ALLOWED_FILE) return
        context.report(ISSUE, reference, context.getLocation(reference), "Use `AppDispatchers` instead of naming a `Dispatchers` member")
    }

    companion object {
        const val ALLOWED_FILE: String = "AndroidDispatchers.kt"

        val ISSUE: Issue = issue(
            "SohvaDispatchers",
            "Dispatcher named outside AppDispatchers",
            "Dispatchers are named in one place (AndroidDispatchers) so bulk work runs at background " +
                "priority and the UI dispatcher keeps cores free for the render thread and the decoder.",
            DispatchersDetector::class.java,
        )
    }
}

/** Infinite animations only through the motion tokens of :ui:design (plan/06 §10.5). */
class InfiniteAnimationDetector : Detector(), SourceCodeScanner {
    override fun getApplicableMethodNames(): List<String> = listOf("rememberInfiniteTransition", "infiniteRepeatable")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (method.containingClass?.qualifiedName?.startsWith("androidx.compose.animation") != true) return
        if (node.getContainingUFile()?.packageName == MOTION_PACKAGE) return
        context.report(ISSUE, node, context.getLocation(node), "Infinite animations live in `$MOTION_PACKAGE` (they must survive a zero animator scale)")
    }

    companion object {
        const val MOTION_PACKAGE: String = "com.sohva.tv.ui.design.motion"

        val ISSUE: Issue = issue(
            "SohvaInfiniteAnimation",
            "Infinite animation outside the motion tokens",
            "Only the buffering arc may animate forever (design/01 §15); it lives in the motion tokens, " +
                "steps in reduced motion and is tested at a zero animator scale.",
            InfiniteAnimationDetector::class.java,
        )
    }
}

/** No `LIMIT … OFFSET` paging in SQL: keyset pages only (AGENTS §4 rule 2). */
class SqlOffsetDetector : Detector(), SourceCodeScanner {
    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(ULiteralExpression::class.java, UPolyadicExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = object : UElementHandler() {
        // A Kotlin string literal is also wrapped in a polyadic expression; check it once.
        override fun visitLiteralExpression(node: ULiteralExpression) {
            if (node.uastParent !is UPolyadicExpression) check(node.value as? String, node)
        }

        override fun visitPolyadicExpression(node: UPolyadicExpression) = check(ConstantEvaluator.evaluateString(context, node, false), node)

        private fun check(text: String?, node: UElement) {
            if (text == null || !OFFSET.containsMatchIn(text)) return
            context.report(ISSUE, node, context.getLocation(node), "Page with a key (`WHERE key > :last ORDER BY key LIMIT n`), not LIMIT/OFFSET")
        }
    }

    companion object {
        private val OFFSET = Regex("""(?is)\bLIMIT\b[^;]*?\bOFFSET\b|\bLIMIT\s+[:?$\w]+\s*,""")

        val ISSUE: Issue = issue(
            "SohvaSqlOffset",
            "LIMIT/OFFSET paging",
            "LIMIT/OFFSET over a sorted query redoes the sort for every page; page along an index with " +
                "`WHERE key > :last ORDER BY key LIMIT n` (AGENTS.md §4 rule 2).",
            SqlOffsetDetector::class.java,
        )
    }
}

/** `clip` followed by `background` on a node: draw a rounded fill instead (design/01 §16.1 rule 7). */
class ClipBackgroundDetector : Detector(), SourceCodeScanner {
    override fun getApplicableMethodNames(): List<String> = listOf("background")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (method.containingClass?.qualifiedName?.startsWith("androidx.compose.foundation") != true) return
        val receiver = node.receiver?.skipParenthesizedExprDown()
        val previous = when (receiver) {
            is UCallExpression -> receiver
            is UQualifiedReferenceExpression -> receiver.selector as? UCallExpression
            else -> null
        } ?: return
        if (previous.methodName != "clip") return
        context.report(ISSUE, node, context.getLocation(node), "Draw the rounded fill with `roundFill`/`drawRoundRect`; clip only nodes that hold an image")
    }

    companion object {
        val ISSUE: Issue = issue(
            "SohvaClipBackground",
            "clip followed by background",
            "A clip per cell costs a layer on low-end GPUs; a rounded fill drawn behind the node looks " +
                "the same (design/01 §16.1 rule 7).",
            ClipBackgroundDetector::class.java,
        )
    }
}
