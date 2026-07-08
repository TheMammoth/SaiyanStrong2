package com.saiyanstrong.presentation.screens.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saiyanstrong.BuildConfig
import com.saiyanstrong.presentation.components.ConfirmDialog
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
fun SettingsScreen(
    onBack: () -> Unit,
    onReplayIntro: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val checkState by viewModel.checkState.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val useFemaleDots by viewModel.useFemaleDotsFormula.collectAsStateWithLifecycle()
    val defaultRestSeconds by viewModel.defaultRestSeconds.collectAsStateWithLifecycle()
    val authUser by viewModel.authUser.collectAsStateWithLifecycle()
    val backupInfo by viewModel.backupInfo.collectAsStateWithLifecycle()
    val isSigningIn by viewModel.isSigningIn.collectAsStateWithLifecycle()
    val isBackingUp by viewModel.isBackingUp.collectAsStateWithLifecycle()
    val isRestoring by viewModel.isRestoring.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(downloadState) {
        if (downloadState is DownloadState.Ready) {
            val uri = (downloadState as DownloadState.Ready).uri
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
            viewModel.consumeInstall()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    if (showRestoreConfirm) {
        val lastBackupLabel = backupInfo?.let {
            DateFormat.getDateTimeInstance().format(Date(it.lastBackupAtMs))
        } ?: "an earlier backup"
        ConfirmDialog(
            title = "RESTORE FROM BACKUP?",
            message = "Local data will be replaced with your backup from $lastBackupLabel. This cannot be undone.",
            confirmLabel = "RESTORE",
            onConfirm = { viewModel.onRestoreBackup() },
            onDismiss = { showRestoreConfirm = false }
        )
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = SaiyanGray,
                    contentColor = Color.White,
                    actionColor = NeonGreen
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .scanlineTexture()
                .padding(padding)
        ) {
            // ── Top bar ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SaiyanGray)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "←",
                    color = NeonGreen,
                    fontSize = 20.sp,
                    modifier = Modifier.clickable { onBack() }.padding(end = 16.dp)
                )
                Text(
                    "SETTINGS",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }

            HorizontalDivider(color = NeonGreen.copy(alpha = 0.25f), thickness = 1.dp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Account section ────────────────────────────────────
                SectionHeader("ACCOUNT")
                val user = authUser
                if (user == null) {
                    Text(
                        "Sign in to back up your training history to the cloud. The app works fully offline either way.",
                        color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp
                    )
                    ActionButton(
                        text = if (isSigningIn) "SIGNING IN…" else "SIGN IN WITH GOOGLE",
                        color = NeonGreen,
                        enabled = !isSigningIn
                    ) {
                        viewModel.onSignInClick(context)
                    }
                } else {
                    SettingsRow("Signed in as", user.email ?: user.displayName ?: "Google account")
                    val lastBackupText = backupInfo?.let {
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.lastBackupAtMs))
                    } ?: "Never"
                    SettingsRow("Last backup", lastBackupText)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            ActionButton(
                                text = if (isBackingUp) "BACKING UP…" else "BACKUP NOW",
                                color = NeonGreen,
                                enabled = !isBackingUp
                            ) { viewModel.onBackupNow() }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            ActionButton(
                                text = if (isRestoring) "RESTORING…" else "RESTORE FROM BACKUP",
                                color = PowerAmber,
                                enabled = !isRestoring && backupInfo != null
                            ) { showRestoreConfirm = true }
                        }
                    }
                    ActionButton(text = "SIGN OUT", color = DangerRed) { viewModel.onSignOut() }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // ── Training section ──────────────────────────────────
                SectionHeader("TRAINING")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SaiyanGray, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Default rest timer", color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "−15s",
                            color = PowerAmber, fontSize = 13.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.clickable { viewModel.onAdjustDefaultRest(-15) }.padding(8.dp)
                        )
                        Text(
                            "%d:%02d".format(defaultRestSeconds / 60, defaultRestSeconds % 60),
                            color = NeonGreen, fontSize = 15.sp, fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                        Text(
                            "+15s",
                            color = PowerAmber, fontSize = 13.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.clickable { viewModel.onAdjustDefaultRest(15) }.padding(8.dp)
                        )
                    }
                }
                Text(
                    "Used for every exercise unless you set a per-exercise rest timer from the ⋮ menu in a workout.",
                    color = Color.White.copy(alpha = 0.35f), fontSize = 11.sp
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SaiyanGray, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                        .clickable { viewModel.onToggleDotsFormula(!useFemaleDots) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("DOTS formula", color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
                    Text(
                        if (useFemaleDots) "FEMALE" else "MALE",
                        color = NeonGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "Tap to switch. DOTS scores your squat + bench + deadlift total relative to bodyweight.",
                    color = Color.White.copy(alpha = 0.35f), fontSize = 11.sp
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // ── About section ─────────────────────────────────────
                SectionHeader("ABOUT")
                SettingsRow("App", "SaiyanStrong")
                SettingsRow("Version", "v${BuildConfig.VERSION_NAME}")
                SettingsRow("Build", "${BuildConfig.VERSION_CODE}")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SaiyanGray, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                        .clickable { onReplayIntro() }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Replay intro", color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
                    Text("▶", color = NeonGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // ── Updates section ───────────────────────────────────
                SectionHeader("UPDATES")

                when (val state = checkState) {
                    is UpdateCheckState.Idle -> {
                        SettingsRow("Status", "Not checked yet")
                    }
                    is UpdateCheckState.Checking -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NeonGreen, strokeWidth = 2.dp)
                            Text("Checking GitHub…", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                        }
                    }
                    is UpdateCheckState.UpToDate -> {
                        SettingsRow("Status", "Up to date  ✓", valueColor = NeonGreen)
                        SettingsRow("Current", "v${state.version}")
                    }
                    is UpdateCheckState.UpdateAvailable -> {
                        SettingsRow("Status", "Update available!", valueColor = PowerAmber)
                        SettingsRow("Current", "v${BuildConfig.VERSION_NAME}")
                        SettingsRow("Latest", state.update.tagName, valueColor = PowerAmber)
                        Spacer(Modifier.height(4.dp))

                        when (val dl = downloadState) {
                            is DownloadState.InProgress -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = PowerAmber, strokeWidth = 2.dp)
                                    Text("Downloading… ${dl.percent}%", color = PowerAmber, fontSize = 13.sp)
                                }
                            }
                            is DownloadState.Failed -> {
                                Text("Download failed: ${dl.reason}", color = DangerRed, fontSize = 12.sp)
                                Spacer(Modifier.height(4.dp))
                                ActionButton("RETRY DOWNLOAD", PowerAmber) {
                                    viewModel.downloadUpdate(state.update)
                                }
                            }
                            else -> {
                                ActionButton("DOWNLOAD ${state.update.tagName}", PowerAmber) {
                                    if (!viewModel.canInstallPackages()) {
                                        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    } else {
                                        viewModel.downloadUpdate(state.update)
                                    }
                                }
                            }
                        }
                    }
                    is UpdateCheckState.Error -> {
                        SettingsRow("Status", "Error", valueColor = DangerRed)
                        Text(
                            state.message,
                            color = DangerRed.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                ActionButton(
                    text = if (checkState is UpdateCheckState.Checking) "CHECKING…" else "CHECK FOR UPDATES",
                    color = NeonGreen,
                    enabled = checkState !is UpdateCheckState.Checking
                ) {
                    viewModel.checkForUpdate()
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // ── Debug section ─────────────────────────────────────
                SectionHeader("DEBUG INFO")
                SettingsRow("Package", "com.saiyanstrong")
                SettingsRow("Version name used for comparison", "v${BuildConfig.VERSION_NAME}")
                SettingsRow("Update API", "api.github.com/repos/TheMammoth/SaiyanStrong2")
            }

            // ── Telemetry bar ─────────────────────────────────────────
            Text(
                "// SaiyanStrong v${BuildConfig.VERSION_NAME} build ${BuildConfig.VERSION_CODE} //",
                color = TelemetryGreen,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
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
private fun SettingsRow(label: String, value: String, valueColor: Color = Color.White) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SaiyanGray, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ActionButton(
    text: String,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.15f),
            contentColor = color,
            disabledContainerColor = Color.White.copy(alpha = 0.05f),
            disabledContentColor = Color.White.copy(alpha = 0.3f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (enabled) color.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text, fontWeight = FontWeight.Black, letterSpacing = 1.sp, fontSize = 13.sp)
    }
}
