package com.example.focusapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.focusapp.BlockingPolicy
import com.example.focusapp.PolicyRepository
import java.time.DayOfWeek
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolicyEditorScreen(
    policyRepository: PolicyRepository,
    existingPolicy: BlockingPolicy?, // null = creating new
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf(existingPolicy?.name ?: "") }
    var startHour by remember { mutableStateOf(existingPolicy?.startTime?.hour ?: 9) }
    var startMinute by remember { mutableStateOf(existingPolicy?.startTime?.minute ?: 0) }
    var endHour by remember { mutableStateOf(existingPolicy?.endTime?.hour ?: 17) }
    var endMinute by remember { mutableStateOf(existingPolicy?.endTime?.minute ?: 0) }
    var selectedDays by remember { 
        mutableStateOf(existingPolicy?.daysOfWeek ?: DayOfWeek.entries.toSet()) 
    }
    
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    
    val isValid = name.isNotBlank() && selectedDays.isNotEmpty()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existingPolicy == null) "New Policy" else "Edit Policy") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val policy = BlockingPolicy(
                                id = existingPolicy?.id ?: java.util.UUID.randomUUID().toString(),
                                name = name,
                                startTime = LocalTime.of(startHour, startMinute),
                                endTime = LocalTime.of(endHour, endMinute),
                                daysOfWeek = selectedDays,
                                isSystemPolicy = false
                            )
                            policyRepository.savePolicy(policy)
                            onSave()
                        },
                        enabled = isValid
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Policy name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Policy Name") },
                placeholder = { Text("e.g., Morning Focus") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            // Time pickers
            Text(
                text = "Blocking Time",
                style = MaterialTheme.typography.labelLarge
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Start time
                OutlinedCard(
                    onClick = { showStartTimePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Start", style = MaterialTheme.typography.labelMedium)
                        Text(
                            String.format("%02d:%02d", startHour, startMinute),
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }
                
                // End time
                OutlinedCard(
                    onClick = { showEndTimePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("End", style = MaterialTheme.typography.labelMedium)
                        Text(
                            String.format("%02d:%02d", endHour, endMinute),
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }
            }
            
            // Days of week
            Text(
                text = "Active Days",
                style = MaterialTheme.typography.labelLarge
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DayOfWeek.entries.forEach { day ->
                    val isSelected = day in selectedDays
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedDays = if (isSelected) {
                                selectedDays - day
                            } else {
                                selectedDays + day
                            }
                        },
                        label = { Text(day.name.take(1)) }
                    )
                }
            }
            
            // Quick select buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { selectedDays = DayOfWeek.entries.toSet() },
                    label = { Text("Every day") }
                )
                AssistChip(
                    onClick = { 
                        selectedDays = setOf(
                            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
                        )
                    },
                    label = { Text("Weekdays") }
                )
                AssistChip(
                    onClick = { 
                        selectedDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                    },
                    label = { Text("Weekends") }
                )
            }
        }
    }
    
    // Time pickers
    if (showStartTimePicker) {
        TimePickerDialog(
            initialHour = startHour,
            initialMinute = startMinute,
            onConfirm = { hour, minute ->
                startHour = hour
                startMinute = minute
                showStartTimePicker = false
            },
            onDismiss = { showStartTimePicker = false }
        )
    }
    
    if (showEndTimePicker) {
        TimePickerDialog(
            initialHour = endHour,
            initialMinute = endMinute,
            onConfirm = { hour, minute ->
                endHour = hour
                endMinute = minute
                showEndTimePicker = false
            },
            onDismiss = { showEndTimePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(timePickerState.hour, timePickerState.minute) }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        text = {
            TimePicker(state = timePickerState)
        }
    )
}
