package com.saiyanstrong.presentation.screens.barpath

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saiyanstrong.presentation.components.SaiyanButton
import com.saiyanstrong.presentation.theme.DangerRed
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.PowerAmber
import com.saiyanstrong.presentation.theme.SaiyanGray

/**
 * Persistent bar for the continuous live session: a "● LIVE" indicator + elapsed timer (session
 * duration since lock-on — NOT video recording, which remains the separate, optional manual
 * RECORD/STOP flow; deliberately not labelled "REC" to avoid implying video is being saved, since
 * this mode saves no video per rep), rep count, RETAP, and END SESSION.
 */
@Composable
fun LiveSessionTopBar(
    elapsedSeconds: Int,
    repCount: Int,
    onRetap: () -> Unit,
    onEndSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(DangerRed, CircleShape))
            Spacer(Modifier.width(4.dp))
            Text("LIVE", color = DangerRed, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(8.dp))
            Text(
                "%d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60),
                color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace
            )
        }
        Text("REP $repCount", color = NeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Row {
            OutlinedButton(
                onClick = onRetap,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PowerAmber),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) { Text("⊕ RETAP", fontSize = 10.sp, fontWeight = FontWeight.Black) }
            Spacer(Modifier.width(6.dp))
            OutlinedButton(
                onClick = onEndSession,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) { Text("✕ END", fontSize = 10.sp, fontWeight = FontWeight.Black) }
        }
    }
}

/**
 * The post-rep summary. Numbers are explicitly labelled "rel. speed" / "px" — UNCALIBRATED,
 * matching the whole live-session scope decision (see BarPathCaptureViewModel). SHARE routes to
 * [onShare] which explains why it doesn't produce a real share image in this mode, rather than
 * silently doing nothing.
 */
@Composable
fun RepSummaryCard(
    summary: BarPathCaptureViewModel.LiveRepSummary,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SaiyanGray, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
    ) {
        Text("REP COMPLETE", color = NeonGreen, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(10.dp))
        RepStatRow("MEAN VELOCITY", "~%.2f (rel. speed)".format(summary.meanVelocityRel))
        RepStatRow("PEAK VELOCITY", "~%.2f (rel. speed)".format(summary.peakVelocityRel))
        RepStatRow("RANGE OF MOTION", "%.0f px".format(summary.romPx))
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SaiyanButton(onClick = onSave, modifier = Modifier.weight(1f)) {
                Text("SAVE", fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = onDiscard,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed)
            ) { Text("DISCARD", fontWeight = FontWeight.Black, fontSize = 12.sp) }
            OutlinedButton(
                onClick = onShare,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.7f))
            ) { Text("SHARE", fontWeight = FontWeight.Black, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun RepStatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
    }
}
