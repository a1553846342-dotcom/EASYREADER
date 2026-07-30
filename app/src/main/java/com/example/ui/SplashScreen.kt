package com.example.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.PreferencesManager
import com.example.ui.theme.clickableWithFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "SplashScreen"

@Composable
fun SplashScreen(
    prefs: PreferencesManager,
    onSplashFinished: () -> Unit
) {
    val context = LocalContext.current
    val scale = remember { Animatable(1.05f) }
    val alpha = remember { Animatable(0f) }
    val splashQuotes = remember(context) {
        val array = context.resources.getStringArray(R.array.splash_quotes)
        if (array.isNotEmpty()) array else arrayOf("如果不战斗，就无法获胜！ ——《进击的巨人》")
    }
    val randomQuote = remember(splashQuotes) { splashQuotes.random() }

    val customPoster = prefs.customSplashPosterUri
    val isPureMode = prefs.splashPureMode

    // List of reliable splash drawable resources
    val splashDrawables = remember {
        listOf(
            R.drawable.splash_1,
            R.drawable.splash_2,
            R.drawable.splash_3,
            R.drawable.cozy_room_banner
        )
    }

    val defaultRandomSplash = remember { splashDrawables.random() }

    var currentImageModel by remember(customPoster, defaultRandomSplash) {
        mutableStateOf<Any>(if (!customPoster.isNullOrEmpty()) customPoster else defaultRandomSplash)
    }

    // Direct synchronous-like async bitmap loader avoiding Coil resource issues
    val loadedBitmap = rememberLoadedSplashBitmap(currentImageModel)

    // Fallback if custom poster fails to decode
    LaunchedEffect(loadedBitmap, currentImageModel) {
        if (loadedBitmap == null && currentImageModel != defaultRandomSplash) {
            Log.w(TAG, "Custom splash model $currentImageModel failed to load, switching to default random splash.")
            currentImageModel = defaultRandomSplash
        }
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
        Log.d(TAG, "SplashScreen initialized. Selected poster model: $currentImageModel")
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
                animationSpec = tween(durationMillis = 400)
            )
            delay(1800)
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
            .background(Color(0xFF0F141C))
            .clickableWithFeedback { safeOnSplashFinished() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    this.alpha = alpha.value
                }
        ) {
            if (loadedBitmap != null) {
                Image(
                    bitmap = loadedBitmap,
                    contentDescription = "开屏海报",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                        }
                )
            } else {
                // Procedural Artistic Fallback Poster (Cozy Night Ambient)
                ProceduralArtisticPoster(randomQuote = randomQuote)
            }

            if (!isPureMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.38f)
                        .align(Alignment.BottomCenter)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))
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
                        fontSize = 26.sp,
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

@Composable
private fun rememberLoadedSplashBitmap(model: Any?): ImageBitmap? {
    val context = LocalContext.current
    val bitmapState = produceState<ImageBitmap?>(initialValue = null, key1 = model) {
        value = withContext(Dispatchers.IO) {
            try {
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 2 // Downsample 2x for fast decoding and low memory
                    inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                }
                when (model) {
                    is Int -> {
                        val bmp = BitmapFactory.decodeResource(context.resources, model, options)
                        bmp?.asImageBitmap()
                    }
                    is String -> {
                        if (model.startsWith("content://") || model.startsWith("file://")) {
                            context.contentResolver.openInputStream(Uri.parse(model))?.use { input ->
                                BitmapFactory.decodeStream(input, null, options)?.asImageBitmap()
                            }
                        } else {
                            val file = File(model)
                            if (file.exists()) {
                                BitmapFactory.decodeFile(file.absolutePath, options)?.asImageBitmap()
                            } else null
                        }
                    }
                    else -> null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode splash bitmap for model $model", e)
                null
            }
        }
    }
    return bitmapState.value
}

@Composable
private fun ProceduralArtisticPoster(randomQuote: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF141923),
                        Color(0xFF1E2838),
                        Color(0xFF0D1118)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.4f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x337FD8C8),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.width * 0.7f
                ),
                center = center,
                radius = size.width * 0.7f
            )
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = 0.08f),
            modifier = Modifier
                .padding(32.dp)
                .wrapContentSize()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoStories,
                    contentDescription = null,
                    tint = Color(0xFF7FD8C8),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "CIallo阅读",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}


