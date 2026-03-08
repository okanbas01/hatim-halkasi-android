package com.example.sharedkhatm.ads

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sharedkhatm.R

/**
 * "Uygulamayı Destekle" alanı: Bugün X kez destek oldunuz, buton (limit dolunca disable).
 * todayCount parent tarafından reward sonrası güncellenir.
 */
@Composable
fun SupportSection(
    supportAdTracker: SupportAdTracker,
    todayCount: Int,
    onSupportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canSupport = supportAdTracker.canSupport()

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Uygulamayı Destekle \uD83D\uDC9D",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Bugün $todayCount kez destek oldunuz",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (canSupport) {
            Button(
                onClick = onSupportClick,
                colors = ButtonDefaults.buttonColors()
            ) {
                Text("Reklam izleyerek destekle")
            }
        } else {
            Text(
                text = "Bugün 3 kez destek oldunuz. Allah razı olsun \uD83D\uDC9D",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
