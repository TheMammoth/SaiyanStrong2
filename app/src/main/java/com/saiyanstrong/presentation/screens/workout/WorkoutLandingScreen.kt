package com.saiyanstrong.presentation.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saiyanstrong.domain.model.WorkoutTemplate
import com.saiyanstrong.presentation.components.ConfirmDialog
import com.saiyanstrong.presentation.components.SaiyanButton
import com.saiyanstrong.presentation.components.scanlineTexture
import com.saiyanstrong.presentation.theme.MatteBlack
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.PowerAmber
import com.saiyanstrong.presentation.theme.SaiyanGray
import com.saiyanstrong.presentation.theme.TelemetryGreen

@Composable
fun WorkoutLandingScreen(
    onStartEmpty: () -> Unit,
    onStartTemplate: (Long) -> Unit,
    onRepeatLast: () -> Unit,
    viewModel: WorkoutLandingViewModel = hiltViewModel()
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val lastSession by viewModel.lastSession.collectAsStateWithLifecycle()

    Scaffold { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .scanlineTexture()
                .background(MatteBlack)
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(
                        "WORKOUT",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        "QUICK START",
                        color = TelemetryGreen, fontSize = 10.sp, letterSpacing = 2.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                SaiyanButton(onClick = onStartEmpty, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "START AN EMPTY WORKOUT",
                        fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 1.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            lastSession?.let { last ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        Modifier.fillMaxWidth()
                            .background(SaiyanGray, RoundedCornerShape(6.dp))
                            .border(1.dp, PowerAmber.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .clickable { onRepeatLast() }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Refresh, null, tint = PowerAmber, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("REPEAT LAST WORKOUT", color = PowerAmber, fontSize = 13.sp,
                                fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                            Text(
                                last.exerciseNames.joinToString(", "),
                                color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "MY TEMPLATES (${templates.size})",
                    color = TelemetryGreen, fontSize = 10.sp, letterSpacing = 2.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            if (templates.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        Modifier.fillMaxWidth()
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No templates yet", color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Finish a workout and tap SAVE AS TEMPLATE",
                            color = Color.White.copy(alpha = 0.35f), fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                items(templates, key = { it.id }) { template ->
                    TemplateCard(
                        template = template,
                        onStart = { onStartTemplate(template.id) },
                        onDelete = { viewModel.onDeleteTemplate(template.id) }
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "tap a template to start · hold to delete",
                        color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(
    template: WorkoutTemplate,
    onStart: () -> Unit,
    onDelete: () -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }
    if (showConfirm) {
        ConfirmDialog(
            title = "DELETE TEMPLATE?",
            message = "\"${template.name}\" (${template.exerciseIds.size} exercises) will be permanently deleted.",
            onConfirm = onDelete,
            onDismiss = { showConfirm = false }
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(SaiyanGray, RoundedCornerShape(6.dp))
            .border(1.dp, NeonGreen.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .pointerInput(template.id) {
                detectTapGestures(
                    onTap = { onStart() },
                    onLongPress = { showConfirm = true }
                )
            }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            template.name,
            color = NeonGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            template.exerciseNames.joinToString(", "),
            color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp,
            maxLines = 3, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.weight(1f))
        Text(
            "${template.exerciseIds.size} exercises",
            color = TelemetryGreen, fontSize = 9.sp, fontFamily = FontFamily.Monospace
        )
    }
}
