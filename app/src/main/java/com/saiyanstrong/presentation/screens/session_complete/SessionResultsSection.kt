package com.saiyanstrong.presentation.screens.session_complete

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import com.saiyanstrong.domain.model.BarPathAnalysis
import com.saiyanstrong.domain.model.SetLog
import com.saiyanstrong.presentation.components.ConfirmDialog
import com.saiyanstrong.presentation.components.SetCell
import com.saiyanstrong.presentation.components.StepperRow
import com.saiyanstrong.presentation.theme.DangerRed
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.PowerAmber
import com.saiyanstrong.presentation.theme.SaiyanGray
import com.saiyanstrong.presentation.theme.TelemetryGreen
import com.saiyanstrong.util.WeightFormatter

private val SetRowBg = Color(0xFF1A1A1A)

private fun Double.fmtKg(): String = WeightFormatter.format(this).replace(" kg", "")

/** Per-exercise card in the RESULTS section: summary header + every logged set, editable inline. */
@Composable
internal fun ExerciseResultCard(
    row: ExerciseRow,
    barPathBySetId: Map<Long, BarPathAnalysis> = emptyMap(),
    onEditSet: (setLogId: Long, weightKg: Double, reps: Int, isFailure: Boolean) -> Unit,
    onDeleteSet: (setLogId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val stepKg = if (row.name.contains("Dumbbell", true) || row.name.contains("Kettlebell", true)) 2.0 else 2.5

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SaiyanGray, RoundedCornerShape(6.dp))
            .padding(vertical = 8.dp, horizontal = 10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            Column(horizontalAlignment = Alignment.End) {
                Text("BEST ${WeightFormatter.format(row.bestWeightKg)}",
                    color = NeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace)
                Text("e1RM ${WeightFormatter.formatOneRm(row.estOneRmKg)}",
                    color = PowerAmber, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }

        if (row.sets.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)) {
                Text("SET",  color = TelemetryGreen, fontSize = 10.sp, modifier = Modifier.width(24.dp))
                Text("KG",   color = TelemetryGreen, fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("REPS", color = TelemetryGreen, fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
            row.sets.forEach { set ->
                EditableResultSetRow(
                    set = set,
                    stepKg = stepKg,
                    barPathAnalysis = barPathBySetId[set.id],
                    onEdit = { kg, reps, fail -> onEditSet(set.id, kg, reps, fail) },
                    onDelete = { onDeleteSet(set.id) },
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun EditableResultSetRow(
    set: SetLog,
    stepKg: Double,
    barPathAnalysis: BarPathAnalysis? = null,
    onEdit: (Double, Int, Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var kgTfv by remember(set.id) { mutableStateOf(TextFieldValue(set.weightKg.fmtKg())) }
    var repsTfv by remember(set.id) { mutableStateOf(TextFieldValue("${set.reps}")) }
    var editFail by remember(set.id) { mutableStateOf(set.isFailure) }
    var focusedCell by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showBarPathDetail by remember(set.id) { mutableStateOf(false) }
    val kgFocus = remember { FocusRequester() }
    val repsFocus = remember { FocusRequester() }
    val focusMgr = LocalFocusManager.current

    fun confirm() {
        val kg = kgTfv.text.toDoubleOrNull() ?: set.weightKg
        val r = repsTfv.text.toIntOrNull() ?: set.reps
        onEdit(kg, r, editFail)
        focusMgr.clearFocus()
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "DELETE SET ${set.setNumber}?",
            message = "${set.weightKg.fmtKg()} kg × ${set.reps} will be removed and totals recalculated.",
            onConfirm = onDelete,
            onDismiss = { showDeleteConfirm = false }
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(SetRowBg, RoundedCornerShape(4.dp))
                .pointerInput(Unit) { detectTapGestures(onLongPress = { showDeleteConfirm = true }) }
                .padding(vertical = 6.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (editFail) "F" else "${set.setNumber}",
                color = if (editFail) DangerRed else Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.width(24.dp).clickable {
                    val f = !editFail; editFail = f
                    onEdit(kgTfv.text.toDoubleOrNull() ?: set.weightKg, repsTfv.text.toIntOrNull() ?: set.reps, f)
                }
            )
            SetCell(
                value = kgTfv, onValueChange = { kgTfv = it },
                keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next,
                onImeAction = { repsFocus.requestFocus() },
                focusRequester = kgFocus,
                onFocusChanged = { if (it) focusedCell = "kg" else if (focusedCell == "kg") focusedCell = null },
                modifier = Modifier.weight(1f)
            )
            SetCell(
                value = repsTfv, onValueChange = { repsTfv = it },
                keyboardType = KeyboardType.Number, imeAction = ImeAction.Done,
                onImeAction = { confirm() },
                focusRequester = repsFocus,
                onFocusChanged = { if (it) focusedCell = "reps" else if (focusedCell == "reps") focusedCell = null },
                modifier = Modifier.weight(1f)
            )
            if (barPathAnalysis != null) {
                Text(
                    "⚡", color = NeonGreen, fontSize = 15.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(start = 6.dp).clickable { showBarPathDetail = !showBarPathDetail }
                )
            }
        }

        when (focusedCell) {
            "kg" -> StepperRow(stepLabel = stepKg.fmtKg(), onStep = { direction ->
                val kg = ((kgTfv.text.toDoubleOrNull() ?: set.weightKg) + direction * stepKg).coerceAtLeast(0.0)
                kgTfv = TextFieldValue(kg.fmtKg())
                onEdit(kg, repsTfv.text.toIntOrNull() ?: set.reps, editFail)
            })
            "reps" -> StepperRow(stepLabel = "1", onStep = { direction ->
                val reps = ((repsTfv.text.toIntOrNull() ?: set.reps) + direction).coerceAtLeast(1)
                repsTfv = TextFieldValue("$reps")
                onEdit(kgTfv.text.toDoubleOrNull() ?: set.weightKg, reps, editFail)
            })
        }

        if (showBarPathDetail && barPathAnalysis != null) {
            BarPathDetailBlock(barPathAnalysis)
        }
    }
}

@Composable
private fun BarPathDetailBlock(analysis: BarPathAnalysis) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(analysis.velocityZone.label, color = NeonGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        BarPathStatLine("Peak velocity", "%.2f m/s".format(analysis.peakVelocityMs))
        BarPathStatLine("Mean velocity", "%.2f m/s".format(analysis.meanConcentricVelocityMs))
        BarPathStatLine("Peak power", "%.0f W".format(analysis.peakPowerWatts))
        BarPathStatLine("Mean power", "%.0f W".format(analysis.meanPowerWatts))
        BarPathStatLine("Range of motion", "%.1f cm".format(analysis.rangeOfMotionCm))
        BarPathStatLine("Bar path deviation", "%.1f cm".format(analysis.barPathDeviationCm))
    }
}

@Composable
private fun BarPathStatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
        Text(value, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}
