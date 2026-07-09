package com.saiyanstrong.presentation.screens.coach

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saiyanstrong.domain.model.CoachLink
import com.saiyanstrong.presentation.components.ConfirmDialog
import com.saiyanstrong.presentation.components.SaiyanButton
import com.saiyanstrong.presentation.components.scanlineTexture
import com.saiyanstrong.presentation.theme.DangerRed
import com.saiyanstrong.presentation.theme.MatteBlack
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.PowerAmber
import com.saiyanstrong.presentation.theme.SaiyanGray
import com.saiyanstrong.presentation.theme.TelemetryGreen
import java.text.DateFormat
import java.util.Date

@Composable
fun CoachSettingsScreen(
    onBack: () -> Unit,
    onViewDashboard: () -> Unit = {},
    viewModel: CoachSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var linkPendingRevoke by remember { mutableStateOf<CoachLink?>(null) }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { snackbarHostState.showSnackbar(it) }
    }

    if (uiState.showConsentDialog) {
        ConsentDialog(
            code = uiState.redeemCodeInput,
            onConfirm = viewModel::onConfirmRedeem,
            onDismiss = viewModel::onDismissConsent
        )
    }

    linkPendingRevoke?.let { link ->
        ConfirmDialog(
            title = "REVOKE COACH ACCESS?",
            message = "${link.coachDisplayName ?: link.coachEmail ?: "This coach"} will immediately lose access to your training history.",
            confirmLabel = "REVOKE",
            onConfirm = { viewModel.onRevokeLink(link.linkId) },
            onDismiss = { linkPendingRevoke = null }
        )
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data, containerColor = SaiyanGray, contentColor = Color.White, actionColor = NeonGreen)
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(MatteBlack)
                .scanlineTexture()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SaiyanGray)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "←", color = NeonGreen, fontSize = 20.sp,
                    modifier = Modifier.clickable { onBack() }.padding(end = 16.dp)
                )
                Text(
                    "COACH MODE",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (uiState.isCoach) {
                    SaiyanButton(
                        onClick = onViewDashboard,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("VIEW COACH DASHBOARD", fontWeight = FontWeight.Black, fontSize = 13.sp)
                    }

                    CoachSectionHeader("INVITE AN ATHLETE")
                    Text(
                        "Generate a code and share it with an athlete. Once they redeem it and consent, " +
                            "you'll be able to see their training history from the Coach Dashboard.",
                        color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp
                    )
                    uiState.generatedCode?.let { code ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SaiyanGray, RoundedCornerShape(6.dp))
                                .border(1.dp, NeonGreen.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(code, color = NeonGreen, fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 3.sp)
                            Text(
                                "SHARE", color = PowerAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "Join me as an athlete on SaiyanStrong! Enter this coach invite code in Settings → Coach Mode: $code")
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share invite code"))
                                }
                            )
                        }
                    }
                    SaiyanButton(
                        onClick = viewModel::onGenerateInviteCode,
                        enabled = !uiState.isGeneratingCode,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (uiState.isGeneratingCode) "GENERATING…"
                            else if (uiState.generatedCode != null) "GENERATE NEW CODE" else "GENERATE CODE",
                            fontWeight = FontWeight.Black, fontSize = 13.sp
                        )
                    }
                }

                CoachSectionHeader("HAVE AN INVITE CODE?")
                Text(
                    "Entering a coach's code lets them see your training history until you revoke it.",
                    color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(SaiyanGray, RoundedCornerShape(6.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        BasicTextField(
                            value = uiState.redeemCodeInput,
                            onValueChange = viewModel::onRedeemCodeInputChange,
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp),
                            cursorBrush = SolidColor(NeonGreen),
                            decorationBox = { inner ->
                                if (uiState.redeemCodeInput.isEmpty()) {
                                    Text("CODE", color = Color.White.copy(alpha = 0.3f), fontSize = 15.sp, fontFamily = FontFamily.Monospace)
                                }
                                inner()
                            }
                        )
                    }
                    SaiyanButton(
                        onClick = viewModel::onRequestRedeem,
                        enabled = !uiState.isRedeeming && uiState.redeemCodeInput.isNotBlank()
                    ) {
                        Text(if (uiState.isRedeeming) "…" else "REDEEM", fontWeight = FontWeight.Black, fontSize = 13.sp)
                    }
                }

                CoachSectionHeader("LINKED COACHES")
                when {
                    uiState.isLoadingLinkedCoaches -> Text("Loading…", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    uiState.linkedCoaches.isEmpty() -> Text(
                        "You haven't linked to a coach yet.",
                        color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp
                    )
                    else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        uiState.linkedCoaches.forEach { link -> LinkedCoachRow(link) { linkPendingRevoke = link } }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkedCoachRow(link: CoachLink, onRevoke: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SaiyanGray, RoundedCornerShape(6.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                link.coachDisplayName ?: link.coachEmail ?: "Coach",
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold
            )
            Text(
                "Linked ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(link.linkedAtMs))}",
                color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp
            )
        }
        Text(
            "REVOKE", color = DangerRed, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onRevoke)
        )
    }
}

@Composable
private fun CoachSectionHeader(title: String) {
    Text(
        title,
        color = TelemetryGreen,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        fontFamily = FontFamily.Monospace
    )
}

@Composable
private fun ConsentDialog(code: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SaiyanGray,
        titleContentColor = Color.White,
        textContentColor = Color.White.copy(alpha = 0.7f),
        title = { Text("SHARE YOUR TRAINING DATA?", fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 1.sp) },
        text = {
            Text(
                "Redeeming code \"$code\" will let that coach see your workout history, exercise records, " +
                    "charts, and Power Level — starting now, until you revoke it. You can revoke access " +
                    "anytime from Settings → Coach Mode → Linked Coaches.",
                fontSize = 13.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("LINK & SHARE", color = PowerAmber, fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    )
}
