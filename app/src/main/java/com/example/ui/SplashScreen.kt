package com.example.ui

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.PreferencesManager
import com.example.ui.theme.clickableWithFeedback
import kotlinx.coroutines.delay

private const val TAG = "SplashScreen"

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

    // List of reliable splash drawable resources
    val splashDrawables = remember {
        listOf(
            R.drawable.splash_1,
            R.drawable.splash_2,
            R.drawable.splash_3,
            R.drawable.cozy_room_banner,
            R.drawable.empty_bookshelf_cat
        )
    }

    val defaultRandomSplash = remember { splashDrawables.random() }

    // Dynamic model state allowing smooth fallback to default image if custom URI fails
    var currentImageModel by remember(customPoster, defaultRandomSplash) {
        mutableStateOf<Any>(if (!customPoster.isNullOrEmpty()) customPoster else defaultRandomSplash)
    }

    var isFinishedCalled by remember { mutableStateOf(false) }
    val safeOnSplashFinished = remember {
        {
            if (!isFinishedCalled) {
                isFinishedCalled = true
                Log.d(TAG, "Navigating away from splash screen")
                onSplashFinished()
            }
        }
    }

    LaunchedEffect(currentImageModel) {
        Log.d(TAG, "SplashScreen initialized. Selected poster model: $currentImageModel, customPosterUri: $customPoster, pureMode: $isPureMode")
    }

    LaunchedEffect(Unit) {
        try {
            scale.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 800)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Scale animation interrupted or failed", e)
        }
    }

    LaunchedEffect(Unit) {
        try {
            alpha.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 500)
            )
            delay(1400)
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 350)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Alpha animation interrupted or failed", e)
        } finally {
            safeOnSplashFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickableWithFeedback { safeOnSplashFinished() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha.value)
        ) {
            AsyncImage(
                model = currentImageModel,
                contentDescription = "开屏海报",
                contentScale = ContentScale.Crop,
                onSuccess = {
                    Log.d(TAG, "Splash poster loaded successfully for model: $currentImageModel")
                },
                onError = { errorState ->
                    Log.e(
                        TAG,
                        "Failed to load splash poster model: $currentImageModel. Switching to fallback.",
                        errorState.result.throwable
                    )
                    if (currentImageModel != defaultRandomSplash) {
                        currentImageModel = defaultRandomSplash
                    }
                },
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

