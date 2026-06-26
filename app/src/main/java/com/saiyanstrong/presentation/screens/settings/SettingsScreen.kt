package com.saiyanstrong.presentation.screens.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.saiyanstrong.presentation.components.scanlineTexture
import com.saiyanstrong.presentation.theme.DangerRed
import com.saiyanstrong.presentation.theme.MatteBlack
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.PowerAmber
import com.saiyanstrong.presentation.theme.SaiyanGray
import com.saiyanstrong.presentation.theme.TelemetryGreen

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val checkState by viewModel.checkState.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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

    Scaffold { padding ->
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
                // ── About section ─────────────────────────────────────
                SectionHeader("ABOUT")
                SettingsRow("App", "SaiyanStrong")
                SettingsRow("Version", "v${BuildConfig.VERSION_NAME}")
                SettingsRow("Build", "${BuildConfig.VERSION_CODE}")

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
                                    Text("Downloading…", color = PowerAmber, fontSize = 13.sp)
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
