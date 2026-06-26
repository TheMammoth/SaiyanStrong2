package com.saiyanstrong.presentation.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saiyanstrong.domain.model.Exercise
import com.saiyanstrong.domain.model.ExerciseCategory
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.PowerAmber
import com.saiyanstrong.presentation.theme.TelemetryGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(
    exercises: List<Exercise>,
    usageCounts: Map<Int, Int> = emptyMap(),
    onExerciseSelected: (Exercise) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ExerciseCategory?>(null) }
    var sortByUsage by remember { mutableStateOf(true) }

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

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search exercises…") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = !sortByUsage,
                        onClick = { sortByUsage = false },
                        label = { Text("A-Z") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonGreen.copy(alpha = 0.2f),
                            selectedLabelColor = NeonGreen
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = sortByUsage,
                        onClick = { sortByUsage = true },
                        label = { Text("RECENT") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonGreen.copy(alpha = 0.2f),
                            selectedLabelColor = NeonGreen
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("ALL") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonGreen.copy(alpha = 0.2f),
                            selectedLabelColor = NeonGreen
                        )
                    )
                }
                items(ExerciseCategory.entries.toList()) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = {
                            selectedCategory = if (selectedCategory == category) null else category
                        },
                        label = { Text(category.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonGreen.copy(alpha = 0.2f),
                            selectedLabelColor = NeonGreen
                        )
                    )
                }
            }

            Text(
                "${filtered.size} exercises",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(filtered, key = { it.id }) { exercise ->
                    val useCount = usageCounts[exercise.id] ?: 0
                    ListItem(
                        headlineContent = { Text(exercise.name) },
                        supportingContent = {
                            Text("${exercise.category.name} · ${exercise.primaryMuscles.first().name.replace('_', ' ')}")
                        },
                        trailingContent = if (useCount > 0) {
                            {
                                Box(
                                    modifier = Modifier
                                        .background(PowerAmber.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                        .border(1.dp, PowerAmber.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "$useCount×",
                                        color = PowerAmber,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onExerciseSelected(exercise) }
                    )
                }
            }
        }
    }
}
