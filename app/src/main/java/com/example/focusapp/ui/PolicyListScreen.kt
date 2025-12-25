package com.example.focusapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.focusapp.BlockingPolicy
import com.example.focusapp.PolicyRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolicyListScreen(
    policyRepository: PolicyRepository,
    onEditPolicy: (BlockingPolicy?) -> Unit // null = create new
) {
    var policies by remember { mutableStateOf(policyRepository.getAllPolicies()) }
    var showDeleteDialog by remember { mutableStateOf<BlockingPolicy?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Blocking Policies") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onEditPolicy(null) }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Policy")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // System policies section
            item {
                Text(
                    text = "System Policies",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp)
                )
            }
            
            items(policies.filter { it.isSystemPolicy }) { policy ->
                PolicyListItem(
                    policy = policy,
                    onClick = { /* System policies are not editable */ },
                    onDelete = null // Can't delete system policies
                )
            }
            
            // User policies section
            item {
                Text(
                    text = "Your Policies",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 24.dp, 16.dp, 8.dp)
                )
            }
            
            val userPolicies = policies.filter { !it.isSystemPolicy }
            if (userPolicies.isEmpty()) {
                item {
                    Text(
                        text = "No custom policies yet. Tap + to create one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                items(userPolicies) { policy ->
                    PolicyListItem(
                        policy = policy,
                        onClick = { onEditPolicy(policy) },
                        onDelete = { showDeleteDialog = policy }
                    )
                }
            }
        }
    }
    
    // Delete confirmation dialog
    showDeleteDialog?.let { policy ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Policy?") },
            text = { Text("Are you sure you want to delete \"${policy.name}\"? This will remove it from all apps.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        policyRepository.deletePolicy(policy.id)
                        policies = policyRepository.getAllPolicies()
                        showDeleteDialog = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PolicyListItem(
    policy: BlockingPolicy,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !policy.isSystemPolicy, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
            if (policy.isActiveNow()) {
                Text(
                    text = "Active now",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
