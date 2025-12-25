package com.example.focusapp

import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.focusapp.ui.PolicyEditorScreen
import com.example.focusapp.ui.PolicyListScreen

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable?
)

// Navigation states
sealed class Screen {
    object AppList : Screen()
    object PolicyList : Screen()
    data class PolicyEditor(val policy: BlockingPolicy?) : Screen()
    data class PolicyAssignment(val app: AppInfo) : Screen()
}

class MainActivity : ComponentActivity() {
    
    private lateinit var policyRepository: PolicyRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        policyRepository = PolicyRepository(this)
        
        setContent {
            MaterialTheme {
                var currentScreen by remember { mutableStateOf<Screen>(Screen.AppList) }
                
                when (val screen = currentScreen) {
                    is Screen.AppList -> {
                        AppListScreen(
                            policyRepository = policyRepository,
                            onNavigateToPolicies = { currentScreen = Screen.PolicyList },
                            onAssignPolicies = { app -> currentScreen = Screen.PolicyAssignment(app) }
                        )
                    }
                    is Screen.PolicyList -> {
                        PolicyListScreen(
                            policyRepository = policyRepository,
                            onEditPolicy = { policy -> currentScreen = Screen.PolicyEditor(policy) },
                            onBack = { currentScreen = Screen.AppList }
                        )
                    }
                    is Screen.PolicyEditor -> {
                        PolicyEditorScreen(
                            policyRepository = policyRepository,
                            existingPolicy = screen.policy,
                            onSave = { currentScreen = Screen.PolicyList },
                            onBack = { currentScreen = Screen.PolicyList }
                        )
                    }
                    is Screen.PolicyAssignment -> {
                        PolicyAssignmentScreen(
                            app = screen.app,
                            policyRepository = policyRepository,
                            onBack = { currentScreen = Screen.AppList }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    policyRepository: PolicyRepository,
    onNavigateToPolicies: () -> Unit,
    onAssignPolicies: (AppInfo) -> Unit
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    
    // Get list of installed apps that have a launcher icon
    val installedApps = remember {
        val mainIntent = android.content.Intent(android.content.Intent.ACTION_MAIN, null)
        mainIntent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        
        packageManager.queryIntentActivities(mainIntent, 0)
            .filter { resolveInfo ->
                resolveInfo.activityInfo.packageName != context.packageName
            }
            .map { resolveInfo ->
                AppInfo(
                    name = resolveInfo.loadLabel(packageManager).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                    icon = resolveInfo.loadIcon(packageManager)
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.name.lowercase() }
    }
    
    // Track assignments (trigger recomposition when assignments change)
    val assignments by remember { mutableStateOf(policyRepository.getAppPolicyAssignments()) }
    
    val partitionedApps = remember(installedApps, assignments) {
        installedApps.partition { app ->
            policyRepository.getPoliciesForApp(app.packageName).isNotEmpty()
        }
    }
    val restrictedApps = partitionedApps.first
    val otherApps = partitionedApps.second

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Focus App") },
                actions = {
                    IconButton(onClick = onNavigateToPolicies) {
                        Icon(Icons.Default.Settings, contentDescription = "Policies")
                    }
                },
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
            val blockedCount = installedApps.count { app ->
                policyRepository.isAppCurrentlyBlocked(app.packageName)
            }
            Text(
                text = "$blockedCount apps currently blocked",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Divider()
            
            // List of apps
            LazyColumn {
                if (restrictedApps.isNotEmpty()) {
                    item {
                        Text(
                            text = "Restricted",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp)
                        )
                    }
                    items(restrictedApps) { app ->
                        val appPolicies = policyRepository.getPoliciesForApp(app.packageName)
                        val isCurrentlyBlocked = appPolicies.any { it.isActiveNow() }
                        
                        AppListItem(
                            app = app,
                            assignedPolicies = appPolicies,
                            isCurrentlyBlocked = isCurrentlyBlocked,
                            onClick = { onAssignPolicies(app) }
                        )
                    }
                }

                if (otherApps.isNotEmpty()) {
                    item {
                        Text(
                            text = "Others",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp)
                        )
                    }
                    items(otherApps) { app ->
                        AppListItem(
                            app = app,
                            assignedPolicies = emptyList(),
                            isCurrentlyBlocked = false,
                            onClick = { onAssignPolicies(app) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppListItem(
    app: AppInfo,
    assignedPolicies: List<BlockingPolicy>,
    isCurrentlyBlocked: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App icon (greyed out if blocked)
        app.icon?.let { drawable ->
            Image(
                bitmap = drawable.toBitmap(48, 48).asImageBitmap(),
                contentDescription = app.name,
                modifier = Modifier.size(48.dp),
                alpha = if (isCurrentlyBlocked) 0.4f else 1f
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // App name and policies
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrentlyBlocked) 
                    MaterialTheme.colorScheme.onSurfaceVariant 
                else 
                    MaterialTheme.colorScheme.onSurface
            )
            
            if (assignedPolicies.isNotEmpty()) {
                Text(
                    text = assignedPolicies.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    text = "No policies assigned",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Status indicator
        if (isCurrentlyBlocked) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "Blocked",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolicyAssignmentScreen(
    app: AppInfo,
    policyRepository: PolicyRepository,
    onBack: () -> Unit
) {
    val allPolicies = remember { policyRepository.getAllPolicies() }
    var assignedPolicyIds by remember { 
        mutableStateOf(policyRepository.getPoliciesForApp(app.packageName).map { it.id }.toSet())
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(app.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
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
            Text(
                text = "Select blocking policies for this app",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
            
            Divider()
            
            LazyColumn {
                items(allPolicies) { policy ->
                    val isAssigned = policy.id in assignedPolicyIds
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isAssigned) {
                                    policyRepository.removePolicyFromApp(app.packageName, policy.id)
                                    assignedPolicyIds = assignedPolicyIds - policy.id
                                } else {
                                    policyRepository.assignPolicyToApp(app.packageName, policy.id)
                                    assignedPolicyIds = assignedPolicyIds + policy.id
                                }
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isAssigned,
                            onCheckedChange = null // Handled by row click
                        )
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = policy.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "${policy.getTimeRangeString()} • ${policy.getDaysString()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        if (policy.isActiveNow()) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "Active",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
