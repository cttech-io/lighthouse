package io.cttech.lighthouse.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cttech.lighthouse.system.CrashReports
import io.cttech.lighthouse.theme.Lh
import io.cttech.lighthouse.ui.components.ConfirmPillButton
import io.cttech.lighthouse.ui.components.FocusOnAppear
import io.cttech.lighthouse.ui.components.PillButton
import io.cttech.lighthouse.ui.components.ProvideHints
import io.cttech.lighthouse.ui.components.popupHints
import io.cttech.lighthouse.ui.components.popupWidth
import io.cttech.lighthouse.ui.components.trapFocus
import kotlinx.coroutines.launch

/**
 * A crash report, shown on the launch after a crash and from Settings → About. Nothing is sent from here:
 * each button hands the report to something the person controls (browser, email app, share sheet) and they
 * send it from there, after seeing it. B closes and keeps the report; Delete removes it.
 */
@Composable
fun CrashReportDialog(report: CrashReports.Report, afterCrash: Boolean, onDelete: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val text = remember { FocusRequester() }
    val firstButton = remember { FocusRequester() }
    val scroll = rememberScrollState()
    var textFocused by remember { mutableStateOf(false) }
    val canEmail = remember { canEmail(context) }
    ProvideHints(popupHints()) { if (it == "B") onClose() }
    Box(
        Modifier
            .fillMaxSize()
            .trapFocus()
            .background(Color.Black.copy(alpha = 0.6f))
            .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && (e.key == Key.ButtonB || e.key == Key.Escape)) { onClose(); true } else false },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.popupWidth(760.dp).background(Lh.colors.surface, RoundedCornerShape(18.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (afterCrash) "Lighthouse closed unexpectedly" else "Last crash report", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "This report was saved on this device and hasn't been sent anywhere. To help fix it, send it one of these ways; " +
                    "you'll see it again before it goes. Report on GitHub needs a GitHub account" +
                    if (canEmail) "; Email it shows us your email address." else ".",
                color = Lh.colors.textMuted, fontSize = 15.sp, lineHeight = 20.sp,
            )
            // The report itself. Focused first: up/down scroll it, and down at the end moves on to the buttons.
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .border(2.dp, if (textFocused) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                    .focusRequester(text)
                    .focusProperties { down = firstButton } // not whichever button happens to be nearest
                    .onFocusChanged { textFocused = it.isFocused }
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val step = when {
                            e.key == Key.DirectionDown && scroll.canScrollForward -> 160f
                            e.key == Key.DirectionUp && scroll.canScrollBackward -> -160f
                            else -> return@onPreviewKeyEvent false
                        }
                        scope.launch { scroll.animateScrollBy(step) }
                        true
                    }
                    .focusable()
                    .verticalScroll(scroll)
                    .padding(12.dp),
            ) {
                Text(report.text, color = Color.White, fontSize = 12.sp, lineHeight = 16.sp, fontFamily = FontFamily.Monospace)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Report on GitHub", Modifier.focusRequester(firstButton)) { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(CrashReports.githubIssueUrl(report)))) }
                if (canEmail) PillButton("Email it") { open(context, emailIntent(report)) }
                PillButton("Share log") {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, CrashReports.emailSubject(report))
                        .putExtra(Intent.EXTRA_TEXT, report.text)
                    open(context, Intent.createChooser(send, "Share crash report"))
                }
                ConfirmPillButton("Delete", "Press A again to delete", onConfirm = onDelete)
            }
        }
    }
    FocusOnAppear(text, report.time)
}

private fun emailIntent(report: CrashReports.Report) = Intent(Intent.ACTION_SENDTO, Uri.parse(CrashReports.mailtoUri(report)))
    // Some email apps read the extras rather than the mailto: query; both carry the same text.
    .putExtra(Intent.EXTRA_EMAIL, arrayOf(CrashReports.EMAIL))
    .putExtra(Intent.EXTRA_SUBJECT, CrashReports.emailSubject(report))
    .putExtra(Intent.EXTRA_TEXT, report.text)

private fun canEmail(context: Context) =
    Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).resolveActivity(context.packageManager) != null

private fun open(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
