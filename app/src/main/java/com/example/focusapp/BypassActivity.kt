package com.example.focusapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class BypassActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "This app"
        
        val bypassManager = BypassManager(this)
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BypassScreen(
                        appName = appName,
                        onCancel = { 
                            // Go back to Home
                            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(homeIntent)
                            finish() 
                        },
                        onConfirm = { duration, reason ->
                            bypassManager.createBypass(packageName, appName, duration, reason)
                            
                            // Launch the target app
                            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                            if (launchIntent != null) {
                                startActivity(launchIntent)
                            }
                            finish()
                        }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"

        fun createIntent(context: Context, packageName: String, appName: String): Intent {
            return Intent(context, BypassActivity::class.java).apply {
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_APP_NAME, appName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BypassScreen(
    appName: String,
    onCancel: () -> Unit,
    onConfirm: (Int, String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    var selectedDuration by remember { mutableIntStateOf(15) }
    
    val durations = listOf(5, 15, 30, 60)
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Focus Mode",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "$appName is currently blocked.",
            style = MaterialTheme.typography.titleMedium,
            fontSize = 18.sp
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Why do you need to use it?",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.align(Alignment.Start)
        )
        
        OutlinedTextField(
            value = reason,
            onValueChange = { reason = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            placeholder = { Text("Write your reason for unblocking...") }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "For how long? (minutes)",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.align(Alignment.Start)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            durations.forEach { duration ->
                FilterChip(
                    selected = selectedDuration == duration,
                    onClick = { selectedDuration = duration },
                    label = { Text("$duration") }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = { onConfirm(selectedDuration, reason) },
            modifier = Modifier.fillMaxWidth(),
            enabled = reason.isNotBlank(),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Bypass Block")
        }
        
        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Go Back")
        }
    }
}
