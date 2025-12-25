package com.example.focusapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

data class LogEntry(
    val timestamp: String,
    val appName: String,
    val duration: String,
    val reason: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    logFilePath: String,
    onBack: () -> Unit
) {
    var logEntries by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    fun loadLog() {
        isLoading = true
        errorMessage = null
        try {
            val file = File(logFilePath)
            if (file.exists()) {
                val content = file.readText()
                logEntries = parseLogs(content).reversed() // Show newest first
            } else {
                logEntries = emptyList()
                errorMessage = "No log file found yet. Bypass an app to create entries."
            }
        } catch (e: Exception) {
            errorMessage = "Error reading log: ${e.message}"
        }
        isLoading = false
    }
    
    LaunchedEffect(logFilePath) {
        loadLog()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bypass History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { loadLog() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                errorMessage != null -> {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp)
                    )
                }
                logEntries.isEmpty() -> {
                    Text(
                        text = "History is empty.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(logEntries) { entry ->
                            LogEntryCard(entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogEntryCard(entry: LogEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = entry.duration,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            
            Text(
                text = entry.timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = entry.reason,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun parseLogs(content: String): List<LogEntry> {
    val entries = mutableListOf<LogEntry>()
    // Split by the delimiter '---'
    val rawSections = content.split("---")
    
    for (section in rawSections) {
        val lines = section.trim().lines().filter { it.isNotBlank() }
        if (lines.size < 2) continue
        
        try {
            // First line: ## 2024-12-25 16:15 — YouTube
            val headerMatch = Regex("^## (.*?) — (.*)$").find(lines[0])
            if (headerMatch != null) {
                val timestamp = headerMatch.groupValues[1]
                val appName = headerMatch.groupValues[2]
                
                // Second line typically starts with **Duration:** [Duration]
                val durationLine = lines.getOrNull(1) ?: ""
                val duration = durationLine.replace("**Duration:** ", "").trim()
                
                // Remainder is the reason
                val reason = lines.drop(2).joinToString("\n").trim()
                
                entries.add(LogEntry(timestamp, appName, duration, reason))
            }
        } catch (e: Exception) {
            // Skip entries that don't match the format (like the old table headers)
            continue
        }
    }
    
    return entries
}
