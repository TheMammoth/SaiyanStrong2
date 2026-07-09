package com.saiyanstrong.presentation.screens.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saiyanstrong.domain.model.WorkoutSession
import com.saiyanstrong.domain.model.WorkoutTemplate
import com.saiyanstrong.presentation.components.SaiyanButton
import com.saiyanstrong.presentation.components.scanlineTexture
import com.saiyanstrong.presentation.theme.DangerRed
import com.saiyanstrong.presentation.theme.MatteBlack
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.PowerAmber
import com.saiyanstrong.presentation.theme.SaiyanGray
import com.saiyanstrong.util.WeightFormatter
import java.text.DateFormat
import java.util.Date

@Composable
fun AthleteDetailScreen(
    onBack: () -> Unit,
    viewModel: AthleteDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val myTemplates by viewModel.myTemplates.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { snackbarHostState.showSnackbar(it) }
    }

    if (uiState.showTemplatePicker) {
        TemplatePickerSheet(
            templates = myTemplates,
            onPick = viewModel::onPushTemplate,
            onDismiss = viewModel::onDismissTemplatePicker
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
                    "ATHLETE HISTORY",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "READ-ONLY",
                    color = PowerAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp
                )
                Text(
                    if (uiState.isPushing) "PUSHING…" else "PUSH TEMPLATE",
                    color = NeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = !uiState.isPushing) { viewModel.onShowTemplatePicker() }
                )
            }

            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading history…", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                }
                uiState.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(uiState.error!!, color = DangerRed, fontSize = 13.sp)
                }
                uiState.sessions.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No sessions logged yet.", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.sessions) { session -> ReadOnlySessionCard(session) }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TemplatePickerSheet(
    templates: List<WorkoutTemplate>,
    onPick: (WorkoutTemplate) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SaiyanGray) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "PUSH A TEMPLATE", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black,
                letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 12.dp)
            )
            if (templates.isEmpty()) {
                Text(
                    "You don't have any templates yet — save one from a finished workout first.",
                    color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp
                )
            } else {
                templates.forEach { template ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(template) }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(template.name, color = NeonGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("${template.exerciseIds.size} exercises", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                        }
                        Text("PUSH", color = PowerAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadOnlySessionCard(session: WorkoutSession) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SaiyanGray, RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Text(
            session.title.ifBlank { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(session.dateMs)) },
            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold
        )
        Text(
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(session.dateMs)),
            color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp
        )
        session.exerciseLogs.forEach { log ->
            val bestSet = log.sets.maxByOrNull { it.weightKg }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${log.sets.size} × ${log.exercise.name}", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                bestSet?.let {
                    Text(
                        "${WeightFormatter.format(it.weightKg)} × ${it.reps}",
                        color = NeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Volume ${WeightFormatter.formatVolume(session.totalVolumeKg)}", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
            Text("+${session.powerEarned} POWER", color = PowerAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
