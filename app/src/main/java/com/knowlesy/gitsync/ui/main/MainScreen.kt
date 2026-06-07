package com.knowlesy.gitsync.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.knowlesy.gitsync.model.SyncLog
import com.knowlesy.gitsync.ui.DirectoryPickerDialog
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel()
) {
    val context = LocalContext.current
    val hasStoragePermission by viewModel.hasStoragePermission.collectAsStateWithLifecycle()
    val isIgnoringBatteryOptimizations by viewModel.isIgnoringBatteryOptimizations.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(0) }
    var showDirPicker by remember { mutableStateOf(false) }

    // Launcher for notification permission (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Notifications are needed to show background sync status.", Toast.LENGTH_LONG).show()
        }
    }

    // Check permissions on resume
    LaunchedEffect(Unit) {
        viewModel.checkPermissionsState()
        viewModel.checkBatteryOptimizationState()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "App Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Git Sync Client", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Default.History, contentDescription = "Logs") },
                    label = { Text("Logs") }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Permission Banner
            if (!hasStoragePermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Storage Permission Required",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "To clone and synchronize local Git folders (like Obsidian vaults), the app needs All Files Access permission.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    }
                                } catch (e: Exception) {
                                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                    context.startActivity(intent)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Grant Permission")
                        }
                    }
                }
            }

            // Battery Optimization Banner
            if (!isIgnoringBatteryOptimizations) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BatteryAlert,
                                contentDescription = "Battery Alert",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = "Disable Battery Optimization",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "To ensure background sync triggers reliably, set battery usage to 'Unrestricted' in system settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Text("Disable Optimization")
                        }
                    }
                }
            }

            // Tabs Content
            if (activeTab == 0) {
                SettingsTab(
                    viewModel = viewModel,
                    isSyncing = isSyncing,
                    onBrowseFolder = { showDirPicker = true }
                )
            } else {
                LogsTab(
                    logs = logs,
                    onClear = { viewModel.clearLogs() },
                    onRefresh = { viewModel.refreshLogs() }
                )
            }
        }
    }

    // Directory Picker Dialog
    if (showDirPicker) {
        DirectoryPickerDialog(
            onDismiss = { showDirPicker = false },
            onSelect = { selectedDir ->
                viewModel.syncFolder.value = selectedDir.absolutePath
                viewModel.saveSettings()
                showDirPicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    viewModel: MainScreenViewModel,
    isSyncing: Boolean,
    onBrowseFolder: () -> Unit
) {
    val context = LocalContext.current
    val gitUrl by viewModel.gitUrl.collectAsStateWithLifecycle()
    val username by viewModel.username.collectAsStateWithLifecycle()
    val email by viewModel.email.collectAsStateWithLifecycle()
    val patToken by viewModel.patToken.collectAsStateWithLifecycle()
    val syncFolder by viewModel.syncFolder.collectAsStateWithLifecycle()
    val syncIntervalMinutes by viewModel.syncIntervalMinutes.collectAsStateWithLifecycle()
    val conflictStrategy by viewModel.conflictStrategy.collectAsStateWithLifecycle()
    val isSyncEnabled by viewModel.isSyncEnabled.collectAsStateWithLifecycle()

    var intervalExpanded by remember { mutableStateOf(false) }
    var strategyExpanded by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Sync Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSyncEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isSyncEnabled) "Automatic Sync Active" else "Automatic Sync Disabled",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isSyncEnabled) "Checking for changes in the background" else "Changes will only sync manually",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = isSyncEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.toggleSyncService(enabled)
                    }
                )
            }
        }

        // Configuration Form
        Text(
            text = "Git Repository Config",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = gitUrl,
            onValueChange = {
                viewModel.gitUrl.value = it
                viewModel.saveSettings()
            },
            label = { Text("Git Remote URL") },
            placeholder = { Text("https://github.com/user/repo.git") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = username,
            onValueChange = {
                viewModel.username.value = it
                viewModel.saveSettings()
            },
            label = { Text("Git Username") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = {
                viewModel.email.value = it
                viewModel.saveSettings()
            },
            label = { Text("Git Email") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = patToken,
            onValueChange = {
                viewModel.patToken.value = it
                viewModel.saveSettings()
            },
            label = { Text("Personal Access Token (PAT)") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Text(
            text = "Sync Target & Strategy",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Folder Picker Textfield
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = syncFolder,
                onValueChange = {
                    viewModel.syncFolder.value = it
                    viewModel.saveSettings()
                },
                label = { Text("Local Sync Folder") },
                readOnly = true,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onBrowseFolder() }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onBrowseFolder,
                modifier = Modifier.height(56.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = "Browse")
            }
        }

        // Interval Dropdown
        ExposedDropdownMenuBox(
            expanded = intervalExpanded,
            onExpandedChange = { intervalExpanded = !intervalExpanded },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            val intervalLabel = getIntervalLabel(syncIntervalMinutes)
            OutlinedTextField(
                readOnly = true,
                value = intervalLabel,
                onValueChange = {},
                label = { Text("Sync Interval") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = intervalExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = intervalExpanded,
                onDismissRequest = { intervalExpanded = false }
            ) {
                val intervals = listOf(1, 5, 10, 30, 60, 90, 180, 300, -1)
                intervals.forEach { time ->
                    DropdownMenuItem(
                        text = { Text(getIntervalLabel(time)) },
                        onClick = {
                            viewModel.syncIntervalMinutes.value = time
                            viewModel.saveSettings()
                            intervalExpanded = false
                        }
                    )
                }
            }
        }

        // Conflict Strategy Dropdown
        ExposedDropdownMenuBox(
            expanded = strategyExpanded,
            onExpandedChange = { strategyExpanded = !strategyExpanded },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            val strategyLabel = getStrategyLabel(conflictStrategy)
            OutlinedTextField(
                readOnly = true,
                value = strategyLabel,
                onValueChange = {},
                label = { Text("Conflict Resolution Strategy") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = strategyExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = strategyExpanded,
                onDismissRequest = { strategyExpanded = false }
            ) {
                val strategies = listOf("CONFLICT_COPY", "KEEP_OURS", "KEEP_THEIRS")
                strategies.forEach { strat ->
                    DropdownMenuItem(
                        text = { Text(getStrategyLabel(strat)) },
                        onClick = {
                            viewModel.conflictStrategy.value = strat
                            viewModel.saveSettings()
                            strategyExpanded = false
                        }
                    )
                }
            }
        }

        // Sync Now Button
        Button(
            onClick = {
                viewModel.syncNow { success, message ->
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            },
            enabled = !isSyncing && viewModel.gitUrl.value.isNotEmpty() && viewModel.syncFolder.value.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            if (isSyncing) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Syncing Repository...")
            } else {
                Icon(Icons.Default.Sync, contentDescription = "Sync Now")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sync Now")
            }
        }
    }
}

@Composable
fun LogsTab(
    logs: List<SyncLog>,
    onClear: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sync Activity History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
                IconButton(onClick = onClear, enabled = logs.isNotEmpty()) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear logs")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No sync activities logged yet.",
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs) { log ->
                    LogItemRow(log)
                }
            }
        }
    }
}

@Composable
fun LogItemRow(log: SyncLog) {
    var expanded by remember { mutableStateOf(false) }
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val dateStr = format.format(Date(log.timestamp))

    val statusColor = when (log.status) {
        "SUCCESS" -> Color(0xFF2E7D32) // Soft Green
        "CONFLICT" -> Color(0xFFF9A825) // Soft Orange/Yellow
        else -> MaterialTheme.colorScheme.error // Soft Red
    }

    val statusIcon = when (log.status) {
        "SUCCESS" -> Icons.Default.CheckCircle
        "CONFLICT" -> Icons.Default.Warning
        else -> Icons.Default.Error
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = log.status,
                    tint = statusColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = log.message,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand",
                    tint = MaterialTheme.colorScheme.outline
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                ) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Status: ${log.status}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor
                    )
                    
                    if (log.changedFiles.isNotEmpty()) {
                        Text(
                            text = "Files Changed (${log.changedFiles.size}):",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                        log.changedFiles.forEach { filePath ->
                            Text(
                                text = "- " + File(filePath).name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "No files modified.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun getIntervalLabel(minutes: Int): String {
    return when (minutes) {
        -1 -> "Manual Only"
        1 -> "Every 1 minute"
        5 -> "Every 5 minutes"
        10 -> "Every 10 minutes"
        30 -> "Every 30 minutes"
        60 -> "Every 1 hour"
        90 -> "Every 1.5 hours"
        180 -> "Every 3 hours"
        300 -> "Every 5 hours"
        else -> "Every $minutes minutes"
    }
}

private fun getStrategyLabel(strategy: String): String {
    return when (strategy) {
        "CONFLICT_COPY" -> "Create Conflict Copies"
        "KEEP_OURS" -> "Keep Local (Ours)"
        "KEEP_THEIRS" -> "Keep Remote (Theirs)"
        else -> strategy
    }
}
