package chat.simplex.common.views.vault

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.platform.BackHandler
import chat.simplex.common.ui.theme.DMSans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val GreenBadge = Color(0xFF4CAF50)
private val AmberBadge = Color(0xFFFF9800)

private sealed class BackupScreen {
    object Main : BackupScreen()
    object EnableSetup : BackupScreen()
    object Restoring : BackupScreen()
    object BackingUp : BackupScreen()
}

@Composable
fun BackupSettingsView(onBack: () -> Unit) {
    var screen by remember { mutableStateOf<BackupScreen>(BackupScreen.Main) }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = screen != BackupScreen.Main) { screen = BackupScreen.Main }
    BackHandler(enabled = screen == BackupScreen.Main) { onBack() }

    when (screen) {
        BackupScreen.Main -> BackupMainScreen(
            onEnableBackup = { screen = BackupScreen.EnableSetup },
            onRestore = { screen = BackupScreen.Restoring },
            onBackupNow = { screen = BackupScreen.BackingUp },
            onDisable = {
                scope.launch {
                    withContext(Dispatchers.IO) { VaultBackup.disableBackup() }
                    screen = BackupScreen.Main
                }
            },
            onBack = onBack
        )

        // After a successful enable: if there are existing un-backed local
        // files, jump straight to the upload screen so the user doesn't have
        // to also tap "Backup Now" manually. Otherwise return to Main.
        BackupScreen.EnableSetup -> EnableBackupScreen(
            onEnabled = {
                val status = VaultBackup.getStatus()
                val needsUpload = status.totalFiles > 0 && status.filesBacked < status.totalFiles
                screen = if (needsUpload) BackupScreen.BackingUp else BackupScreen.Main
            },
            onBack = { screen = BackupScreen.Main }
        )

        BackupScreen.Restoring -> RestoringScreen(
            onDone = { screen = BackupScreen.Main }
        )

        BackupScreen.BackingUp -> BackingUpScreen(
            onDone = { screen = BackupScreen.Main }
        )
    }
}

// ═══════════════ MAIN BACKUP SETTINGS ═══════════════

@Composable
private fun BackupMainScreen(
    onEnableBackup: () -> Unit,
    onRestore: () -> Unit,
    onBackupNow: () -> Unit,
    onDisable: () -> Unit,
    onBack: () -> Unit
) {
    var status by remember { mutableStateOf(BackupStatus()) }

    LaunchedEffect(Unit) {
        status = withContext(Dispatchers.IO) { VaultBackup.getStatus() }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        BackupTopBar("Cloud Backup", onBack = onBack)

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                shape = RoundedCornerShape(12.dp),
                elevation = 0.dp,
                backgroundColor = Color.Transparent
            ) {
                Box(
                    Modifier.fillMaxWidth()
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF1A237E), Color(0xFF1F4CFF))),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(46.dp).clip(CircleShape)
                                    .background(MaterialTheme.colors.onPrimary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (status.enabled) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                                    null, Modifier.size(24.dp), tint = MaterialTheme.colors.onPrimary
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    if (status.enabled) "Backup Enabled" else "Backup Disabled",
                                    fontSize = 20.sp, fontFamily = DMSans,
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onPrimary
                                )
                                Text(
                                    "End-to-End Encrypted",
                                    fontSize = 12.sp, fontFamily = DMSans,
                                    color = MaterialTheme.colors.onPrimary.copy(alpha = 0.8f)
                                )
                            }
                        }
                        if (status.enabled) {
                            Spacer(Modifier.height(16.dp))
                            Divider(color = MaterialTheme.colors.onPrimary.copy(alpha = 0.2f))
                            Spacer(Modifier.height(12.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                StatItem("Files", "${status.filesBacked}/${status.totalFiles}")
                                StatItem("Last Backup", if (status.lastBackupAtMs > 0) formatTimeAgo(status.lastBackupAtMs) else "Never")
                                StatItem("Status", if (status.filesBacked == status.totalFiles) "Synced" else "Pending")
                            }
                        }
                    }
                }
            }

            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp), elevation = 1.dp, backgroundColor = MaterialTheme.colors.surface
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Security", fontSize = 14.sp, fontFamily = DMSans, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.onBackground)
                    Spacer(Modifier.height(14.dp))
                    SecurityRow(Icons.Filled.Lock, "AES-256-GCM Encryption", "Military-grade file encryption", GreenBadge)
                    Spacer(Modifier.height(12.dp))
                    SecurityRow(Icons.Filled.VpnKey, "PBKDF2 Key Derivation", "Keys derived from your Pro code", MaterialTheme.colors.primary)
                    Spacer(Modifier.height(12.dp))
                    SecurityRow(Icons.Filled.Shield, "Zero-Knowledge Server", "Server cannot read your data", AmberBadge)
                    Spacer(Modifier.height(12.dp))
                    SecurityRow(Icons.Filled.Fingerprint, "Anonymous Pro Code", "Your code is the only credential needed", GreenBadge)
                }
            }

            if (status.enabled) {
                ActionCard("Backup Now", "Upload all pending files", Icons.Filled.CloudUpload, MaterialTheme.colors.primary, onBackupNow)
                ActionCard("Restore From Cloud", "Download and decrypt files from cloud", Icons.Filled.CloudDownload, GreenBadge, onRestore)

                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onDisable,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colors.error.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Icon(Icons.Filled.CloudOff, null, Modifier.size(18.dp), tint = MaterialTheme.colors.error)
                    Spacer(Modifier.width(6.dp))
                    Text("Disable Cloud Backup", fontFamily = DMSans, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.error, fontSize = 14.sp)
                }
            } else {
                Button(
                    onClick = onEnableBackup,
                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.elevation(0.dp, 0.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Filled.CloudUpload, null, tint = MaterialTheme.colors.onPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Enable Cloud Backup", color = MaterialTheme.colors.onPrimary, fontFamily = DMSans, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                OutlinedButton(
                    onClick = onRestore,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Icon(Icons.Filled.CloudDownload, null, Modifier.size(18.dp), tint = MaterialTheme.colors.primary)
                    Spacer(Modifier.width(6.dp))
                    Text("Restore From Cloud", fontFamily = DMSans, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.primary, fontSize = 14.sp)
                }
            }

            // Always visible: emergency reset for orphaned / un-decryptable server-side blobs.
            Spacer(Modifier.height(8.dp))
            ResetCloudButton()

            Spacer(Modifier.height(20.dp))
        }
    }
}

// ═══════════════ ENABLE BACKUP ═══════════════

@Composable
private fun EnableBackupScreen(onEnabled: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colors.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BackupTopBar("Enable Cloud Backup", onBack = onBack)

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            Box(
                Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colors.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.CloudUpload, null, Modifier.size(36.dp), tint = MaterialTheme.colors.primary)
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "End-to-End Encrypted Backup",
                fontSize = 20.sp, fontFamily = DMSans,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Your vault data will be encrypted on your device before uploading. " +
                    "The server cannot read your files. Your Pro account code is the only " +
                    "credential needed — both to sign in and to decrypt your backup on any device.",
                fontSize = 14.sp, fontFamily = DMSans,
                color = MaterialTheme.colors.secondary, textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            WarningCard(
                "If you lose your Pro account code, your backup CANNOT be recovered. " +
                    "Store it somewhere safe."
            )

            Spacer(Modifier.height(20.dp))

            FeatureRow(Icons.Filled.Lock, "AES-256-GCM encryption for every file", GreenBadge)
            Spacer(Modifier.height(10.dp))
            FeatureRow(Icons.Filled.Shield, "Zero-knowledge — server sees only encrypted blobs", MaterialTheme.colors.primary)
            Spacer(Modifier.height(10.dp))
            FeatureRow(Icons.Filled.VpnKey, "PBKDF2-derived keys — your code is the only secret", AmberBadge)
            Spacer(Modifier.height(10.dp))
            FeatureRow(Icons.Filled.Sync, "Auto-sync new files to cloud", GreenBadge)

            Spacer(Modifier.height(32.dp))

            error?.let {
                Text(it, fontSize = 13.sp, fontFamily = DMSans, color = MaterialTheme.colors.error, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
            }

            Button(
                onClick = {
                    loading = true; error = null
                    scope.launch {
                        val result = VaultBackup.enableBackup()
                        loading = false
                        if (result.success) {
                            onEnabled()
                        } else {
                            error = result.error ?: "Failed to enable backup"
                        }
                    }
                },
                enabled = !loading,
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary),
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.elevation(0.dp, 0.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colors.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Connecting...", color = MaterialTheme.colors.onPrimary, fontFamily = DMSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                } else {
                    Icon(Icons.Filled.CloudUpload, null, tint = MaterialTheme.colors.onPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Enable Cloud Backup", color = MaterialTheme.colors.onPrimary, fontFamily = DMSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ═══════════════ RESTORING PROGRESS ═══════════════

@Composable
private fun RestoringScreen(onDone: () -> Unit) {
    var progress by remember { mutableStateOf(BackupProgressInfo(0, 0, false)) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val result = VaultBackup.restoreFromCloud { p -> progress = p }
        progress = result
        resultMessage = when {
            result.total == 0 && result.errors > 0 ->
                "Could not connect — check that you're signed in to Pro and try again."
            result.total == 0 ->
                "Nothing to restore — your cloud backup is empty."
            result.errors == 0 ->
                "Successfully restored ${result.current} file${if (result.current != 1) "s" else ""}"
            else ->
                "Restored ${result.current} file${if (result.current != 1) "s" else ""} with ${result.errors} error${if (result.errors != 1) "s" else ""}"
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colors.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!progress.done) {
            CircularProgressIndicator(color = MaterialTheme.colors.primary, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(24.dp))
            Text("Restoring Your Vault", fontSize = 20.sp, fontFamily = DMSans, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onBackground)
            Spacer(Modifier.height(8.dp))
            Text(
                "Downloading and decrypting files...",
                fontSize = 14.sp, fontFamily = DMSans, color = MaterialTheme.colors.secondary
            )
            if (progress.total > 0) {
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = (progress.current.toFloat() / progress.total).coerceIn(0f, 1f),
                    color = MaterialTheme.colors.primary,
                    modifier = Modifier.fillMaxWidth(0.6f).height(4.dp).clip(RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${progress.current} / ${progress.total}",
                    fontSize = 14.sp, fontFamily = DMSans, fontWeight = FontWeight.Medium, color = MaterialTheme.colors.onBackground
                )
            }
        } else {
            val isSuccess = progress.errors == 0 && progress.total > 0
            val isEmpty = progress.total == 0 && progress.errors == 0
            Box(
                Modifier.size(72.dp).clip(CircleShape)
                    .background(
                        when {
                            isSuccess -> GreenBadge.copy(alpha = 0.10f)
                            isEmpty -> MaterialTheme.colors.primary.copy(alpha = 0.10f)
                            else -> AmberBadge.copy(alpha = 0.10f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when {
                        isSuccess -> Icons.Filled.CheckCircle
                        isEmpty -> Icons.Filled.CloudOff
                        else -> Icons.Filled.Warning
                    },
                    null, Modifier.size(36.dp),
                    tint = when {
                        isSuccess -> GreenBadge
                        isEmpty -> MaterialTheme.colors.primary
                        else -> AmberBadge
                    }
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                when {
                    isSuccess -> "Restore Complete"
                    isEmpty -> "Nothing to Restore"
                    else -> "Restore Finished"
                },
                fontSize = 20.sp, fontFamily = DMSans, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onBackground
            )
            Spacer(Modifier.height(8.dp))
            resultMessage?.let {
                Text(it, fontSize = 14.sp, fontFamily = DMSans, color = MaterialTheme.colors.secondary, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
            }
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onDone,
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary),
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.elevation(0.dp, 0.dp),
                modifier = Modifier.height(44.dp).fillMaxWidth(0.6f)
            ) {
                Text("Done", color = MaterialTheme.colors.onPrimary, fontFamily = DMSans, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ═══════════════ BACKING UP PROGRESS ═══════════════

@Composable
private fun BackingUpScreen(onDone: () -> Unit) {
    var progress by remember { mutableStateOf(BackupProgressInfo(0, 0, false)) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val result = VaultBackup.backupAll { p -> progress = p }
        progress = result
        resultMessage = when {
            result.total == 0 -> "All files are already backed up"
            result.errors == 0 -> "Backed up ${result.current} file${if (result.current != 1) "s" else ""} successfully"
            else -> "Backed up ${result.current} file${if (result.current != 1) "s" else ""}, ${result.errors} failed"
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colors.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!progress.done) {
            CircularProgressIndicator(color = MaterialTheme.colors.primary, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(24.dp))
            Text("Backing Up", fontSize = 20.sp, fontFamily = DMSans, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onBackground)
            Spacer(Modifier.height(8.dp))
            Text("Encrypting and uploading files...", fontSize = 14.sp, fontFamily = DMSans, color = MaterialTheme.colors.secondary)
            if (progress.total > 0) {
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = (progress.current.toFloat() / progress.total).coerceIn(0f, 1f),
                    color = MaterialTheme.colors.primary,
                    modifier = Modifier.fillMaxWidth(0.6f).height(4.dp).clip(RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.height(8.dp))
                Text("${progress.current} / ${progress.total}", fontSize = 14.sp, fontFamily = DMSans, fontWeight = FontWeight.Medium, color = MaterialTheme.colors.onBackground)
            }
        } else {
            val isSuccess = progress.errors == 0
            Box(
                Modifier.size(72.dp).clip(CircleShape)
                    .background(if (isSuccess) GreenBadge.copy(alpha = 0.10f) else AmberBadge.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isSuccess) Icons.Filled.CloudDone else Icons.Filled.Warning,
                    null, Modifier.size(36.dp),
                    tint = if (isSuccess) GreenBadge else AmberBadge
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                if (isSuccess) "Backup Complete" else "Backup Finished",
                fontSize = 20.sp, fontFamily = DMSans, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onBackground
            )
            Spacer(Modifier.height(8.dp))
            resultMessage?.let {
                Text(it, fontSize = 14.sp, fontFamily = DMSans, color = MaterialTheme.colors.secondary, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onDone,
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary),
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.elevation(0.dp, 0.dp),
                modifier = Modifier.height(44.dp).fillMaxWidth(0.6f)
            ) {
                Text("Done", color = MaterialTheme.colors.onPrimary, fontFamily = DMSans, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ═══════════════ RESET CLOUD STORAGE ═══════════════

/**
 * Wipes every blob on the vault server for the signed-in account.
 *
 * This exists because the encrypted index ("meta") and backup blobs are keyed
 * by the user's Pro code via PBKDF2. If older blobs are left over from prior
 * sessions (different key derivation, a previous reinstall, test runs), the
 * "Restore From Cloud" path can pick one up that won't decrypt. This button
 * lets the user start fresh without having to dig into a database.
 */
@Composable
private fun ResetCloudButton() {
    val scope = rememberCoroutineScope()
    var showConfirm by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    TextButton(
        onClick = { showConfirm = true },
        modifier = Modifier.fillMaxWidth().height(38.dp),
        enabled = !running
    ) {
        Icon(Icons.Filled.DeleteSweep, null, Modifier.size(16.dp), tint = MaterialTheme.colors.error.copy(alpha = 0.8f))
        Spacer(Modifier.width(6.dp))
        Text(
            if (running) "Wiping cloud storage..." else "Reset Cloud Storage",
            fontFamily = DMSans,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            color = MaterialTheme.colors.error.copy(alpha = 0.8f)
        )
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { if (!running) showConfirm = false },
            shape = RoundedCornerShape(12.dp),
            title = {
                Text(
                    "Reset cloud storage?",
                    fontFamily = DMSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colors.onSurface
                )
            },
            text = {
                Text(
                    "This deletes every file stored in your Dexgram cloud backup. " +
                        "Your local vault is NOT affected — you can re-enable backup " +
                        "afterwards to upload a fresh copy.\n\n" +
                        "Use this if Restore From Cloud is failing because of leftover " +
                        "data from an earlier session.",
                    fontFamily = DMSans,
                    fontSize = 14.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.85f)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        running = true
                        scope.launch {
                            val count = withContext(Dispatchers.IO) { VaultBackup.wipeServerStorage() }
                            running = false
                            showConfirm = false
                            resultMessage = when {
                                count < 0 -> "Could not reach the server. Check your Pro sign-in."
                                count == 0 -> "Cloud storage was already empty."
                                else -> "Deleted $count file${if (count != 1) "s" else ""} from cloud."
                            }
                        }
                    },
                    enabled = !running
                ) {
                    Text(
                        if (running) "Working..." else "Delete Everything",
                        color = MaterialTheme.colors.error,
                        fontFamily = DMSans,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!running) showConfirm = false }, enabled = !running) {
                    Text("Cancel", fontFamily = DMSans, color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f))
                }
            }
        )
    }

    resultMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { resultMessage = null },
            shape = RoundedCornerShape(12.dp),
            title = {
                Text("Done", fontFamily = DMSans, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colors.onSurface)
            },
            text = {
                Text(msg, fontFamily = DMSans, fontSize = 14.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.85f))
            },
            confirmButton = {
                TextButton(onClick = { resultMessage = null }) {
                    Text("OK", color = MaterialTheme.colors.primary, fontFamily = DMSans, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}

// ═══════════════ SHARED COMPONENTS ═══════════════

@Composable
private fun BackupTopBar(title: String, onBack: () -> Unit) {
    Surface(elevation = 2.dp, color = MaterialTheme.colors.surface) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = MaterialTheme.colors.onBackground) }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 20.sp, fontFamily = DMSans, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onBackground)
            }
            Icon(Icons.Filled.Lock, "Encrypted", Modifier.size(16.dp), tint = GreenBadge)
            Spacer(Modifier.width(4.dp))
            Text("E2EE", fontSize = 11.sp, fontFamily = DMSans, fontWeight = FontWeight.Medium, color = GreenBadge)
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 15.sp, fontFamily = DMSans, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onPrimary)
        Text(label, fontSize = 11.sp, fontFamily = DMSans, color = MaterialTheme.colors.onPrimary.copy(alpha = 0.7f))
    }
}

@Composable
private fun SecurityRow(icon: ImageVector, title: String, subtitle: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(20.dp), tint = color)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontSize = 14.sp, fontFamily = DMSans, fontWeight = FontWeight.Medium, color = MaterialTheme.colors.onBackground)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 12.sp, fontFamily = DMSans, color = MaterialTheme.colors.secondary)
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, text: String, color: Color) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(16.dp), tint = color)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = 14.sp, fontFamily = DMSans, fontWeight = FontWeight.Medium, color = MaterialTheme.colors.onBackground)
    }
}

@Composable
private fun WarningCard(text: String) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = 0.dp,
        backgroundColor = MaterialTheme.colors.error.copy(alpha = 0.08f)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Warning, null, Modifier.size(18.dp), tint = MaterialTheme.colors.error)
            Spacer(Modifier.width(12.dp))
            Text(text, fontSize = 13.sp, fontFamily = DMSans, color = MaterialTheme.colors.error.copy(alpha = 0.9f))
        }
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp), elevation = 1.dp, backgroundColor = MaterialTheme.colors.surface
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(24.dp), tint = color)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontFamily = DMSans, fontWeight = FontWeight.Medium, color = MaterialTheme.colors.onBackground)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, fontFamily = DMSans, color = MaterialTheme.colors.secondary)
            }
            Icon(Icons.Filled.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colors.secondary)
        }
    }
}

private fun formatTimeAgo(ms: Long): String {
    val diff = System.currentTimeMillis() - ms
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> "${diff / 86_400_000}d ago"
    }
}
