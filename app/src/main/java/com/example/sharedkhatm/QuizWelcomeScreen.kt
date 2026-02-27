package com.example.sharedkhatm

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val PrimaryGreen = Color(0xFF1B5E20)
private val White = Color.White

/**
 * Giriş ekranı: quiz_welcome_page.webp full background (ContentScale.Crop).
 * Başlık sayfanın üst tarafında; header ile arasında 24–48dp responsive boşluk.
 * MaterialTheme.typography (headlineMedium) korunur; animasyon/blur/Lottie/AsyncImage/LaunchedEffect yok.
 */
@Composable
fun QuizWelcomeScreen(
    viewModel: QuizWelcomeViewModel,
    onStartClick: () -> Unit,
    onContinueClick: () -> Unit,
    onRestartClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val isTablet = screenWidth >= 600

    val horizontalPadding = if (isTablet) 32.dp else 24.dp
    val topSpacerDp = if (isTablet) 48.dp else 36.dp
    val bottomSpacerDp = if (isTablet) 40.dp else 28.dp

    val titleStyle = MaterialTheme.typography.headlineMedium
    val bodyMediumStyle = MaterialTheme.typography.bodyMedium
    val bodyLargeStyle = MaterialTheme.typography.bodyLarge
    val buttonTextStyle = MaterialTheme.typography.titleMedium

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.quiz_welcome_page),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f))
                .padding(horizontal = horizontalPadding),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .widthIn(max = 600.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(topSpacerDp))
                Text(
                    text = if (state.isGameOver) "Yeniden Başlamaya Hazır Mısın?" else "İslami Bilgi Yarışmasına Hoşgeldiniz",
                    style = titleStyle,
                    fontWeight = FontWeight.Bold,
                    color = White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.weight(1f))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = if (state.isGameOver) onRestartClick else onStartClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = if (state.isGameOver) "Yeniden Başla" else "Başla",
                        color = White,
                        style = buttonTextStyle,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Doğru cevapladıkça puan artar. Yanlışta oyun biter.",
                    style = bodyMediumStyle,
                    color = White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "En Yüksek Puan: ${state.highScore}",
                    style = bodyLargeStyle,
                    color = White
                )
                Text(
                    text = if (state.lastScore >= 0) "Son Puan: ${state.lastScore}" else "Son Puan: -",
                    style = bodyLargeStyle,
                    color = White
                )
                if (state.hasContinueState && !state.isGameOver) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onContinueClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = White),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(text = "Devam et", style = buttonTextStyle)
                    }
                }
                Spacer(modifier = Modifier.height(bottomSpacerDp))
            }
        }
    }
}
