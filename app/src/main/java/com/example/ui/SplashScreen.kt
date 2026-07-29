package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.PreferencesManager
import com.example.ui.theme.MintGold
import com.example.ui.theme.MintPrimary
import com.example.ui.theme.MintSecondary
import com.example.ui.theme.clickableWithFeedback
import kotlinx.coroutines.delay

val LiteraryQuotes = listOf(
    "粗缯大布裹生涯，腹有诗书气自华。—— 苏轼",
    "书卷多情似故人，晨昏忧乐每相亲。—— 于谦",
    "读书破万卷，下笔如有神。—— 杜甫",
    "立身以立学为先，立学以读书为本。—— 欧阳修",
    "发愤识遍天下字，立志读尽人间书。—— 苏轼",
    "旧书不厌百回读，熟读深思子自知。—— 苏轼",
    "路漫漫其修远兮，吾将上下而求索。—— 屈原",
    "博观而约取，厚积而薄发。—— 苏轼"
)

@Composable
fun SplashScreen(
    prefs: PreferencesManager,
    onSplashFinished: () -> Unit
) {
    val scale = remember { Animatable(1.05f) }
    val alpha = remember { Animatable(0f) }
    val randomQuote = remember { LiteraryQuotes.random() }

    val customPoster = prefs.customSplashPosterUri
    val isPureMode = prefs.splashPureMode

    var isFinishedCalled by remember { mutableStateOf(false) }
    val safeOnSplashFinished = remember {
        {
            if (!isFinishedCalled) {
                isFinishedCalled = true
                onSplashFinished()
            }
        }
    }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(durationMillis = 800)
        )
    }

    LaunchedEffect(Unit) {
        alpha.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(durationMillis = 500)
        )
        delay(1400)
        alpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 350)
        )
        safeOnSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickableWithFeedback { safeOnSplashFinished() },
        contentAlignment = Alignment.Center
    ) {
        if (!customPoster.isNullOrEmpty()) {
            Box(modifier = Modifier.fillMaxSize().alpha(alpha.value)) {
                AsyncImage(
                    model = customPoster,
                    contentDescription = "开屏海报",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(scale.value)
                )

                if (!isPureMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.35f)
                            .align(Alignment.BottomCenter)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "CIallo阅读",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = randomQuote,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(alpha.value)
            ) {
                val splashImages = listOf(
                    com.example.R.drawable.splash_1,
                    com.example.R.drawable.splash_2,
                    com.example.R.drawable.splash_3
                )
                val randomSplash = remember { splashImages.random() }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(alpha.value)
                ) {
                    AsyncImage(
                        model = randomSplash,
                        contentDescription = "Splash Illustration",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(scale.value)
                    )

                    // Gradient overlay to make text readable
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.35f)
                            .align(Alignment.BottomCenter)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "CIallo阅读",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = randomQuote,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center
                        )
                    }

                    Text(
                        text = "点击跳过",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    )
                }
            }
        }
    }
}
