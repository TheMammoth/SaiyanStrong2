package com.saiyanstrong.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saiyanstrong.presentation.theme.MatteBlack
import com.saiyanstrong.presentation.theme.NeonGreen

private val RPE_VALUES = listOf(6f, 6.5f, 7f, 7.5f, 8f, 8.5f, 9f, 9.5f, 10f)

private fun Float.rpeLabel(): String =
    if (this == this.toInt().toFloat()) "${toInt()}" else "$this"

/** Bottom sheet for picking a set's RPE — chip grid 6–10 in 0.5 steps, plus a clear option. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RpeBottomSheet(
    currentRpe: Float?,
    onSelect: (Float?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MatteBlack
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                "RPE",
                color = NeonGreen, fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp
            )
            Text(
                "RPE is a way to measure the difficulty of a set. Tap a number to select an RPE value.",
                color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(RPE_VALUES) { value ->
                    val selected = currentRpe == value
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (selected) NeonGreen.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f),
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                1.dp,
                                if (selected) NeonGreen else Color.White.copy(alpha = 0.15f),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelect(value) }
                            .padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            value.rpeLabel(),
                            color = if (selected) NeonGreen else Color.White,
                            fontSize = 15.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Text(
                "NO RPE",
                color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 8.dp)
                    .clickable { onSelect(null) }
                    .padding(vertical = 10.dp)
            )
        }
    }
}
