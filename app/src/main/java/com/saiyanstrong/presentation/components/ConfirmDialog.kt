package com.saiyanstrong.presentation.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.saiyanstrong.presentation.theme.DangerRed
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.SaiyanGray

/** Destructive-action confirmation in the app's dark industrial style. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "DELETE",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SaiyanGray,
        titleContentColor = Color.White,
        textContentColor = Color.White.copy(alpha = 0.7f),
        title = { Text(title, fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 1.sp) },
        text = { Text(message, fontSize = 13.sp) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = DangerRed, fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    )
}
