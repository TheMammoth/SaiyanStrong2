package com.saiyanstrong.presentation.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saiyanstrong.domain.model.SaiyanStage
import com.saiyanstrong.presentation.components.SaiyanButton
import com.saiyanstrong.presentation.components.ScouterGauge
import com.saiyanstrong.presentation.theme.DangerRed
import com.saiyanstrong.presentation.theme.MatteBlack
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.PowerAmber
import com.saiyanstrong.presentation.theme.SaiyanGray
import com.saiyanstrong.presentation.theme.TelemetryGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 4

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val gateState by viewModel.gateState.collectAsStateWithLifecycle()

    LaunchedEffect(gateState) {
        if (gateState is OnboardingGateState.SkipToHome) onFinished()
    }

    Box(Modifier.fillMaxSize().background(MatteBlack)) {
        if (gateState is OnboardingGateState.ShowOnboarding) {
            OnboardingPager(
                viewModel = viewModel,
                onFinished = {
                    viewModel.onFinished()
                    onFinished()
                }
            )
        }
    }
}

@Composable
private fun OnboardingPager(viewModel: OnboardingViewModel, onFinished: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val isSigningIn by viewModel.isSigningIn.collectAsStateWithLifecycle()
    val signInError by viewModel.signInError.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            if (pagerState.currentPage < PAGE_COUNT - 1) {
                TextButton(onClick = onFinished) {
                    Text("SKIP", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, letterSpacing = 1.sp)
                }
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> LogSetsPage()
                1 -> EarnPowerPage(isCurrentPage = pagerState.currentPage == 1)
                2 -> EvolvePage()
                else -> FinalPage(
                    isSigningIn = isSigningIn,
                    signInError = signInError,
                    signedIn = signedIn,
                    onSignIn = { viewModel.onSignInClick(context) },
                    onBegin = onFinished
                )
            }
        }

        PageIndicator(
            pageCount = PAGE_COUNT,
            currentPage = pagerState.currentPage,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
        )

        if (pagerState.currentPage < PAGE_COUNT - 1) {
            SaiyanButton(
                onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 20.dp)
            ) {
                Text(
                    "NEXT",
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun PageIndicator(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.Center) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (index == currentPage) 10.dp else 7.dp)
                    .background(
                        if (index == currentPage) NeonGreen else Color.White.copy(alpha = 0.25f),
                        CircleShape
                    )
            )
        }
    }
}

// ── Page 1 — LOG YOUR SETS ────────────────────────────────────────────────

@Composable
private fun LogSetsPage() {
    OnboardingPageScaffold(
        eyebrow = "01 // TRACK",
        title = "LOG YOUR SETS",
        body = "Every set, every rep, every kg — tracked in seconds."
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(SaiyanGray, RoundedCornerShape(8.dp))
                .border(1.dp, NeonGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Text(
                "BENCH PRESS (BARBELL)",
                color = NeonGreen, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                MockHeaderCell("SET")
                MockHeaderCell("PREV")
                MockHeaderCell("KG")
                MockHeaderCell("REPS")
                MockHeaderCell("")
            }
            MockSetRow(set = "1", prev = "80×8", kg = "82.5", reps = "8", done = true)
            MockSetRow(set = "2", prev = "80×8", kg = "82.5", reps = "7", done = true)
            MockSetRow(set = "3", prev = "—", kg = "82.5", reps = "", done = false)
        }
    }
}

@Composable
private fun MockCell(text: String, color: Color) {
    Text(text, color = color, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
        modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
}

@Composable
private fun MockHeaderCell(text: String) {
    Text(
        text, color = TelemetryGreen, fontSize = 9.sp, letterSpacing = 1.sp,
        fontFamily = FontFamily.Monospace, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center
    )
}

@Composable
private fun MockSetRow(set: String, prev: String, kg: String, reps: String, done: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (done) Color(0xFF1A3A1A) else Color.Transparent, RoundedCornerShape(4.dp))
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MockCell(set, Color.White.copy(alpha = 0.7f))
        MockCell(prev, Color.White.copy(alpha = 0.4f))
        MockCell(kg, if (done) NeonGreen else Color.White)
        MockCell(reps, if (done) NeonGreen else Color.White)
        Text(
            if (done) "✓" else "",
            color = NeonGreen, fontSize = 13.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.width(48.dp), textAlign = TextAlign.Center
        )
    }
}

// ── Page 2 — EARN POWER ───────────────────────────────────────────────────

@Composable
private fun EarnPowerPage(isCurrentPage: Boolean) {
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            progress = 0f
            delay(350)
            progress = 0.68f
        }
    }

    OnboardingPageScaffold(
        eyebrow = "02 // PROGRESS",
        title = "EARN POWER",
        body = "Every kg of volume you lift feeds your Power Level. Heavier sets, more reps, bigger score."
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ScouterGauge(
                powerCurrent = (9_001 + 25_199 * progress).toInt(),
                stageLabel = "Super Saiyan",
                progressToNext = progress
            )
        }
    }
}

// ── Page 3 — EVOLVE ───────────────────────────────────────────────────────

@Composable
private fun EvolvePage() {
    OnboardingPageScaffold(
        eyebrow = "03 // EVOLUTION",
        title = "EVOLVE",
        body = "Cross each threshold and your Saiyan stage transforms."
    ) {
        Column(Modifier.fillMaxWidth()) {
            SaiyanStage.entries.forEach { stage ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .background(SaiyanGray, RoundedCornerShape(6.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stage.label.uppercase(),
                        color = NeonGreen, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp
                    )
                    Text(
                        "%,d".format(stage.threshold),
                        color = PowerAmber, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

// ── Page 4 — final CTA ────────────────────────────────────────────────────

@Composable
private fun FinalPage(
    isSigningIn: Boolean,
    signInError: String?,
    signedIn: Boolean,
    onSignIn: () -> Unit,
    onBegin: () -> Unit
) {
    OnboardingPageScaffold(
        eyebrow = "04 // READY",
        title = "TIME TO TRAIN",
        body = "Your first workout is one tap away."
    ) {
        Column(Modifier.fillMaxWidth()) {
            SaiyanButton(onClick = onBegin, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "▶  BEGIN TRAINING",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            if (signedIn) {
                Text(
                    "✓ SIGNED IN — BACKUPS ENABLED",
                    color = NeonGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                )
            } else {
                TextButton(
                    onClick = onSignIn,
                    enabled = !isSigningIn,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (isSigningIn) "SIGNING IN…" else "Back up your power — sign in with Google",
                        color = if (isSigningIn) Color.White.copy(alpha = 0.4f) else PowerAmber,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold
                    )
                }
                if (signInError != null) {
                    Text(
                        signInError, color = DangerRed, fontSize = 10.sp,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ── Shared page scaffold ──────────────────────────────────────────────────

@Composable
private fun OnboardingPageScaffold(
    eyebrow: String,
    title: String,
    body: String,
    content: @Composable () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            eyebrow, color = TelemetryGreen, fontSize = 10.sp, letterSpacing = 3.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(6.dp))
        Text(
            title, color = Color.White, style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black, letterSpacing = 1.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            body, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(28.dp))
        content()
    }
}
