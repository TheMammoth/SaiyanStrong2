package com.saiyanstrong.presentation.screens.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saiyanstrong.domain.model.Exercise
import com.saiyanstrong.domain.model.ExerciseCategory
import com.saiyanstrong.presentation.components.scanlineTexture
import com.saiyanstrong.presentation.theme.MatteBlack
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.PowerAmber
import com.saiyanstrong.presentation.theme.SaiyanGray
import com.saiyanstrong.presentation.theme.TelemetryGreen

@Composable
fun ExerciseBrowserScreen(
    viewModel: ExerciseBrowserViewModel = hiltViewModel()
) {
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val usageCounts by viewModel.usageCounts.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ExerciseCategory?>(null) }
    var sortByUsage by remember { mutableStateOf(false) }

    val filtered = remember(exercises, searchQuery, selectedCategory, sortByUsage) {
        exercises
            .filter { ex ->
                (searchQuery.isBlank() || ex.name.contains(searchQuery.trim(), ignoreCase = true)) &&
                (selectedCategory == null || ex.category == selectedCategory)
            }
            .let { list ->
                if (sortByUsage)
                    list.sortedWith(compareByDescending<Exercise> { usageCounts[it.id] ?: 0 }.thenBy { it.name })
                else
                    list.sortedBy { it.name }
            }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .scanlineTexture()
                .background(MatteBlack)
                .padding(padding)
        ) {
            // ── Header ────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SaiyanGray)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    "EXERCISES",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }
            HorizontalDivider(color = NeonGreen.copy(alpha = 0.25f), thickness = 1.dp)

            // ── Search ────────────────────────────────────────────────
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search exercises…", color = Color.White.copy(alpha = 0.4f)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonGreen,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = NeonGreen
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )

            // ── Filter chips ──────────────────────────────────────────
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                item { BrowserChip(!sortByUsage, "A-Z") { sortByUsage = false } }
                item { BrowserChip(sortByUsage, "MOST USED") { sortByUsage = true } }
                item { BrowserChip(selectedCategory == null, "ALL") { selectedCategory = null } }
                items(ExerciseCategory.entries.toList()) { cat ->
                    BrowserChip(selectedCategory == cat, cat.name) {
                        selectedCategory = if (selectedCategory == cat) null else cat
                    }
                }
            }

            Text(
                "${filtered.size} exercises",
                color = TelemetryGreen,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            // ── List ──────────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filtered, key = { it.id }) { exercise ->
                    BrowserExerciseRow(exercise, usageCounts[exercise.id] ?: 0)
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                }
            }
        }
    }
}

@Composable
private fun BrowserExerciseRow(exercise: Exercise, usageCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(exercise.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                "${exercise.category.name} · ${exercise.primaryMuscles.first().name.replace('_', ' ')}",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp
            )
        }
        if (usageCount > 0) {
            Box(
                modifier = Modifier
                    .background(PowerAmber.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                    .border(1.dp, PowerAmber.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("$usageCount", color = PowerAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BrowserChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = NeonGreen.copy(alpha = 0.2f),
            selectedLabelColor = NeonGreen,
            containerColor = SaiyanGray,
            labelColor = Color.White.copy(alpha = 0.6f)
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            selectedBorderColor = NeonGreen.copy(alpha = 0.5f),
            borderColor = Color.White.copy(alpha = 0.15f)
        )
    )
}
