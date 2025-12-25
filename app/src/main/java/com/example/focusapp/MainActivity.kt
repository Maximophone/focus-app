package com.example.focusapp

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable?
)

class MainActivity : ComponentActivity() {
    
    private lateinit var blockedAppsRepository: BlockedAppsRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        blockedAppsRepository = BlockedAppsRepository(this)
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppControlPanel(blockedAppsRepository)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppControlPanel(repository: BlockedAppsRepository) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    
    // Get list of installed apps that have a launcher icon
    val installedApps = remember {
        val mainIntent = android.content.Intent(android.content.Intent.ACTION_MAIN, null)
        mainIntent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        
        packageManager.queryIntentActivities(mainIntent, 0)
            .filter { resolveInfo ->
                // Exclude our own app
                resolveInfo.activityInfo.packageName != context.packageName
            }
            .map { resolveInfo ->
                val appInfo = resolveInfo.activityInfo.applicationInfo
                AppInfo(
                    name = resolveInfo.loadLabel(packageManager).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                    icon = resolveInfo.loadIcon(packageManager)
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.name.lowercase() }
    }
    
    // Track blocked state for each app
    var blockedApps by remember { mutableStateOf(repository.getBlockedApps()) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Focus App") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header with count
            Text(
                text = "${blockedApps.size} apps blocked",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Divider()
            
            // List of apps with toggles
            LazyColumn {
                items(installedApps) { app ->
                    AppListItem(
                        app = app,
                        isBlocked = blockedApps.contains(app.packageName),
                        onToggle = { isBlocked ->
                            if (isBlocked) {
                                repository.blockApp(app.packageName)
                            } else {
                                repository.unblockApp(app.packageName)
                            }
                            blockedApps = repository.getBlockedApps()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AppListItem(
    app: AppInfo,
    isBlocked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App icon
        app.icon?.let { drawable ->
            Image(
                bitmap = drawable.toBitmap(48, 48).asImageBitmap(),
                contentDescription = app.name,
                modifier = Modifier.size(48.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // App name and package
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.name,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Block toggle
        Switch(
            checked = isBlocked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.error,
                checkedTrackColor = MaterialTheme.colorScheme.errorContainer
            )
        )
    }
}
