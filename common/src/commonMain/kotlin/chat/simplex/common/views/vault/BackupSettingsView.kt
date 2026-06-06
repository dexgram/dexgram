package chat.simplex.common.views.vault

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    /** Lists cloud files with thumbnails so the user can pick what to restore. */
    object RestoreSelect : BackupScreen()
    /** Downloads the chosen files (null selectedIds = restore everything). */
    data class Restoring(val selectedIds: Set<String>?) : BackupScreen()
    object BackingUp : BackupScreen()
    /** Shows the freshly generated 24-word phrase right after first enable. */
    data class ShowPhrase(val phrase: String) : BackupScreen()
    /** Collects the 24-word phrase before restoring on a new device. */
    object EnterPhrase : BackupScreen()
    /** Lets the user view their stored recovery phrase. */
    object ViewPhrase : BackupScreen()
}

@Composable
fun BackupSettingsView(startRestore: Boolean = false, onBack: () -> Unit) {
    var screen by remember {
        mutableStateOf<BackupScreen>(
            if (startRestore) {
                // Deep-linked from the vault's "Restore from Cloud" shortcut.
                if (VaultBackup.hasBackupPassphrase()) BackupScreen.RestoreSelect else BackupScreen.EnterPhrase
            } else BackupScreen.Main
        )
    }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = screen != BackupScreen.Main) { screen = BackupScreen.Main }
    BackHandler(enabled = screen == BackupScreen.Main) { onBack() }

    // Where to go after enable / phrase confirmation: upload pending files or Main.
    fun afterEnable() {
        val status = VaultBackup.getStatus()
        val needsUpload = status.totalFiles > 0 && status.filesBacked < status.totalFiles
        screen = if (needsUpload) BackupScreen.BackingUp else BackupScreen.Main
    }

    when (val s = screen) {
        BackupScreen.Main -> BackupMainScreen(
            onEnableBackup = { screen = BackupScreen.EnableSetup },
            onRestore = {
                // Restore needs the recovery phrase. If this device doesn't have
                // it yet (fresh install), collect it first. Then show the picker.
                screen = if (VaultBackup.hasBackupPassphrase()) BackupScreen.RestoreSelect else BackupScreen.EnterPhrase
            },
            onBackupNow = { screen = BackupScreen.BackingUp },
            onViewPhrase = { screen = BackupScreen.ViewPhrase },
            onDisable = {
                scope.launch {
                    withContext(Dispatchers.IO) { VaultBackup.disableBackup() }
                    screen = BackupScreen.Main
                }
            },
            onBack = onBack
        )

        // After a successful enable: if a brand-new recovery phrase was created,
        // show it so the user can save it before continuing. Otherwise proceed
        // straight to uploading pending files (or back to Main).
        BackupScreen.EnableSetup -> EnableBackupScreen(
            onEnabled = { newPhrase ->
                if (newPhrase != null) screen = BackupScreen.ShowPhrase(newPhrase)
                else afterEnable()
            },
            onBack = { screen = BackupScreen.Main }
        )

        is BackupScreen.ShowPhrase -> RecoveryPhraseScreen(
            phrase = s.phrase,
            isFirstTime = true,
            onDone = { afterEnable() },
            onBack = { afterEnable() }
        )

        BackupScreen.ViewPhrase -> {
            val stored = remember { VaultBackup.getBackupPassphrase() }
            if (stored.isNullOrBlank()) {
                LaunchedEffect(Unit) { screen = BackupScreen.Main }
            } else {
                RecoveryPhraseScreen(
                    phrase = stored,
                    isFirstTime = false,
                    onDone = { screen = BackupScreen.Main },
                    onBack = { screen = BackupScreen.Main }
                )
            }
        }

        BackupScreen.EnterPhrase -> EnterPhraseScreen(
            onConfirmed = { phrase ->
                VaultBackup.setBackupPassphrase(phrase)
                screen = BackupScreen.RestoreSelect
            },
            onBack = { screen = BackupScreen.Main }
        )

        BackupScreen.RestoreSelect -> RestoreSelectScreen(
            onRestore = { selectedIds -> screen = BackupScreen.Restoring(selectedIds) },
            onBack = { screen = BackupScreen.Main }
        )

        is BackupScreen.Restoring -> RestoringScreen(
            selectedIds = s.selectedIds,
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
    onViewPhrase: () -> Unit,
    onDisable: () -> Unit,
    onBack: () -> Unit
) {
    var status by remember { mutableStateOf(BackupStatus()) }
    var usage by remember { mutableStateOf<VaultStorageUsage?>(null) }
    var devices by remember { mutableStateOf<List<chat.simplex.common.views.chatlist.DexgramDevice>?>(null) }

    LaunchedEffect(Unit) {
        status = withContext(Dispatchers.IO) { VaultBackup.getStatus() }
        usage = withContext(Dispatchers.IO) { VaultBackup.getStorageUsage() }
        devices = when (val r = chat.simplex.common.views.chatlist.DexgramApi.getDevices()) {
            is chat.simplex.common.views.chatlist.ApiResult.Success -> r.data
            is chat.simplex.common.views.chatlist.ApiResult.Error -> null
        }
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

            usage?.let { StorageUsageCard(it) }

            devices?.let { DevicesCard(it) }

            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp), elevation = 1.dp, backgroundColor = MaterialTheme.colors.surface
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Security", fontSize = 14.sp, fontFamily = DMSans, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.onBackground)
                    Spacer(Modifier.height(14.dp))
                    SecurityRow(Icons.Filled.Lock, "AES-256-GCM Encryption", "Military-grade file encryption", GreenBadge)
                    Spacer(Modifier.height(12.dp))
                    SecurityRow(Icons.Filled.VpnKey, "24-Word Recovery Phrase", "Encrypts your backup — keep it secret", MaterialTheme.colors.primary)
                    Spacer(Modifier.height(12.dp))
                    SecurityRow(Icons.Filled.Shield, "Zero-Knowledge Server", "Server cannot read your data", AmberBadge)
                    Spacer(Modifier.height(12.dp))
                    SecurityRow(Icons.Filled.Fingerprint, "Code Leak Protection", "Sharing your Pro code can't expose the backup", GreenBadge)
                }
            }

            if (status.enabled) {
                ActionCard("Backup Now", "Upload all pending files", Icons.Filled.CloudUpload, MaterialTheme.colors.primary, onBackupNow)
                ActionCard("Restore From Cloud", "Download and decrypt files from cloud", Icons.Filled.CloudDownload, GreenBadge, onRestore)
                ActionCard("Recovery Phrase", "View your 24-word recovery phrase", Icons.Filled.VpnKey, AmberBadge, onViewPhrase)

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
private fun EnableBackupScreen(onEnabled: (newPassphrase: String?) -> Unit, onBack: () -> Unit) {
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
                "Your vault is encrypted on this device before uploading — the server only " +
                    "ever sees encrypted blobs. We'll generate a 24-word recovery phrase that " +
                    "encrypts your backup, so even if your Pro code is shared, your files stay private.",
                fontSize = 14.sp, fontFamily = DMSans,
                color = MaterialTheme.colors.secondary, textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            WarningCard(
                "You'll see your 24-word recovery phrase next. Write it down and keep it safe — " +
                    "without it your backup CANNOT be restored on a new device."
            )

            Spacer(Modifier.height(20.dp))

            FeatureRow(Icons.Filled.Lock, "AES-256-GCM encryption for every file", GreenBadge)
            Spacer(Modifier.height(10.dp))
            FeatureRow(Icons.Filled.Shield, "Zero-knowledge — server sees only encrypted blobs", MaterialTheme.colors.primary)
            Spacer(Modifier.height(10.dp))
            FeatureRow(Icons.Filled.VpnKey, "24-word recovery phrase encrypts your backup", AmberBadge)
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
                            onEnabled(result.newPassphrase)
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

// ═══════════════ RESTORE — PICK FILES ═══════════════

@Composable
private fun RestoreSelectScreen(
    onRestore: (Set<String>?) -> Unit,
    onBack: () -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var files by remember { mutableStateOf<List<CloudFileInfo>>(emptyList()) }
    val selected = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) { VaultBackup.listCloudFiles() }
        files = result.files
        error = result.error
        loading = false
    }

    val allSelected = files.isNotEmpty() && selected.size == files.size

    Column(Modifier.fillMaxSize().background(MaterialTheme.colors.background).statusBarsPadding()) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 24.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = MaterialTheme.colors.onBackground)
            }
            Column(Modifier.weight(1f)) {
                Text("Choose Files to Restore", fontSize = 18.sp, fontFamily = DMSans, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onBackground)
                if (!loading && error == null) {
                    Text(
                        if (files.isEmpty()) "No files in your backup"
                        else "${selected.size} of ${files.size} selected",
                        fontSize = 12.sp, fontFamily = DMSans, color = MaterialTheme.colors.secondary
                    )
                }
            }
            if (!loading && error == null && files.isNotEmpty()) {
                TextButton(onClick = {
                    if (allSelected) selected.clear()
                    else { selected.clear(); selected.addAll(files.map { it.id }) }
                }) {
                    Text(if (allSelected) "Clear" else "Select All", color = MaterialTheme.colors.primary, fontFamily = DMSans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colors.primary, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Reading your backup…", fontSize = 14.sp, fontFamily = DMSans, color = MaterialTheme.colors.secondary)
                }

                error != null -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Filled.CloudOff, null, Modifier.size(48.dp), tint = AmberBadge)
                    Spacer(Modifier.height(16.dp))
                    Text(error!!, fontSize = 14.sp, fontFamily = DMSans, color = MaterialTheme.colors.secondary, textAlign = TextAlign.Center)
                }

                files.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Filled.CloudOff, null, Modifier.size(48.dp), tint = MaterialTheme.colors.primary)
                    Spacer(Modifier.height(16.dp))
                    Text("Your cloud backup is empty.", fontSize = 14.sp, fontFamily = DMSans, color = MaterialTheme.colors.secondary, textAlign = TextAlign.Center)
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(files, key = { it.id }) { file ->
                        RestoreFileTile(
                            file = file,
                            checked = file.id in selected,
                            onToggle = {
                                if (file.id in selected) selected.remove(file.id)
                                else selected.add(file.id)
                            }
                        )
                    }
                }
            }
        }

        // Bottom action bar
        if (!loading && error == null && files.isNotEmpty()) {
            Surface(elevation = 8.dp, color = MaterialTheme.colors.surface) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Button(
                        onClick = { onRestore(selected.toSet()) },
                        enabled = selected.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary),
                        shape = RoundedCornerShape(12.dp),
                        elevation = ButtonDefaults.elevation(0.dp, 0.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Filled.CloudDownload, null, Modifier.size(20.dp), tint = MaterialTheme.colors.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (selected.isEmpty()) "Select files to restore"
                            else "Restore ${selected.size} file${if (selected.size != 1) "s" else ""}",
                            color = MaterialTheme.colors.onPrimary, fontFamily = DMSans, fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onRestore(null) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Text("Restore All Files", color = MaterialTheme.colors.primary, fontFamily = DMSans, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RestoreFileTile(
    file: CloudFileInfo,
    checked: Boolean,
    onToggle: () -> Unit
) {
    var bitmap by remember(file.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(file.id) {
        val bytes = file.thumbnailBytes
        if (bytes != null) {
            bitmap = withContext(Dispatchers.IO) { decodeVaultImage(bytes) }
        }
    }

    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colors.surface)
            .border(
                width = if (checked) 2.dp else 1.dp,
                color = if (checked) MaterialTheme.colors.primary else MaterialTheme.colors.onBackground.copy(alpha = 0.08f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onToggle() }
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bmp, contentDescription = file.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(
                Modifier.fillMaxSize().background(fileTypeColor(file.fileType).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(fileTypeIcon(file.fileType), null, Modifier.size(34.dp), tint = fileTypeColor(file.fileType))
            }
        }

        // Video play overlay
        if (file.fileType == VaultFileType.VIDEO && bmp != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayCircle, null, Modifier.size(34.dp), tint = Color.White)
            }
        }

        // Dim + check overlay when selected
        if (checked) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colors.primary.copy(alpha = 0.18f)))
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape)
                .background(if (checked) MaterialTheme.colors.primary else Color.Black.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (checked) Icons.Filled.Check else Icons.Filled.RadioButtonUnchecked,
                null, Modifier.size(15.dp), tint = Color.White
            )
        }

        // Name + size footer
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(file.name, color = Color.White, fontSize = 10.sp, fontFamily = DMSans, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatSize(file.sizeBytes), color = Color.White.copy(alpha = 0.8f), fontSize = 9.sp, fontFamily = DMSans)
        }
    }
}

// ═══════════════ RESTORING PROGRESS ═══════════════

@Composable
private fun RestoringScreen(selectedIds: Set<String>?, onDone: () -> Unit) {
    var progress by remember { mutableStateOf(BackupProgressInfo(0, 0, false)) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val result = VaultBackup.restoreFromCloud(selectedIds) { p -> progress = p }
        progress = result
        resultMessage = when {
            result.total == 0 && result.errors > 0 ->
                "Couldn't read your backup. Check your internet, that you're signed in to Pro, " +
                    "and that your 24-word recovery phrase is correct, then try again."
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
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf(BackupProgressInfo(0, 0, false)) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var started by remember { mutableStateOf(false) }

    // Password-protected folders that still hold files needing upload. These
    // can't be backed up without the folder password, so we collect them first.
    val lockedFolders = remember {
        val idx = VaultStorage.getIndex()
        idx.folders.filter { f ->
            f.hasPassword && idx.files.any { it.folderId == f.id && it.isDoubleEncrypted && !it.backedUp }
        }
    }
    var collecting by remember { mutableStateOf(lockedFolders.isNotEmpty()) }

    fun run(folderPasswords: Map<String, String>) {
        if (started) return
        started = true
        collecting = false
        scope.launch {
            val result = VaultBackup.backupAll(folderPasswords) { p -> progress = p }
            progress = result
            resultMessage = when {
                result.total == 0 -> "Folder structure backed up. No new files to upload."
                result.errors == 0 -> "Backed up ${result.current} file${if (result.current != 1) "s" else ""} successfully"
                else -> "Backed up ${result.current} file${if (result.current != 1) "s" else ""}, ${result.errors} failed"
            }
        }
    }

    if (collecting) {
        FolderPasswordCollectScreen(
            folders = lockedFolders,
            onContinue = { pwds -> run(pwds) },
            onSkip = { run(emptyMap()) }
        )
        return
    }

    if (!started) {
        LaunchedEffect(Unit) { run(emptyMap()) }
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

// ═══════════════ LOCKED FOLDER PASSWORD COLLECTION ═══════════════

@Composable
private fun FolderPasswordCollectScreen(
    folders: List<VaultFolder>,
    onContinue: (Map<String, String>) -> Unit,
    onSkip: () -> Unit
) {
    val entered = remember { mutableStateMapOf<String, String>() }
    val errors = remember { mutableStateMapOf<String, Boolean>() }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colors.background).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        Icon(Icons.Filled.Lock, null, Modifier.size(40.dp), tint = MaterialTheme.colors.primary)
        Spacer(Modifier.height(16.dp))
        Text(
            "Unlock Folders to Back Up",
            fontSize = 20.sp, fontFamily = DMSans, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.onBackground, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "These folders are password-protected. Enter each password to include its files in the backup, or skip to back up everything else.",
            fontSize = 13.sp, fontFamily = DMSans, color = MaterialTheme.colors.secondary, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        Column(
            Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
        ) {
            folders.forEach { folder ->
                OutlinedTextField(
                    value = entered[folder.id] ?: "",
                    onValueChange = { entered[folder.id] = it; errors[folder.id] = false },
                    label = { Text(folder.name, fontFamily = DMSans) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = errors[folder.id] == true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errors[folder.id] == true) {
                    Text(
                        "Incorrect password",
                        color = MaterialTheme.colors.error, fontSize = 12.sp, fontFamily = DMSans,
                        modifier = Modifier.align(Alignment.Start).padding(start = 4.dp, top = 2.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val verified = mutableMapOf<String, String>()
                var anyError = false
                folders.forEach { folder ->
                    val pwd = entered[folder.id]?.takeIf { it.isNotBlank() }
                    if (pwd != null) {
                        if (VaultStorage.verifyFolderPassword(folder.id, pwd)) {
                            verified[folder.id] = pwd
                        } else {
                            errors[folder.id] = true
                            anyError = true
                        }
                    }
                }
                if (!anyError) onContinue(verified)
            },
            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary),
            shape = RoundedCornerShape(12.dp),
            elevation = ButtonDefaults.elevation(0.dp, 0.dp),
            modifier = Modifier.height(46.dp).fillMaxWidth()
        ) {
            Text("Continue", color = MaterialTheme.colors.onPrimary, fontFamily = DMSans, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("Skip locked folders", fontFamily = DMSans, color = MaterialTheme.colors.secondary)
        }
    }
}

// ═══════════════ RECOVERY PHRASE ═══════════════

@Composable
private fun RecoveryPhraseScreen(
    phrase: String,
    isFirstTime: Boolean,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val words = remember(phrase) { phrase.trim().split(Regex("\\s+")).filter { it.isNotEmpty() } }
    val clipboard = LocalClipboardManager.current
    var revealed by remember { mutableStateOf(!isFirstTime) }
    var confirmed by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        BackupTopBar(if (isFirstTime) "Save Recovery Phrase" else "Recovery Phrase", onBack = onBack)

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colors.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.VpnKey, null, Modifier.size(32.dp), tint = MaterialTheme.colors.primary) }

            Spacer(Modifier.height(16.dp))
            Text(
                "Your 24-Word Recovery Phrase",
                fontSize = 19.sp, fontFamily = DMSans, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onBackground, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "This phrase encrypts your backup. You'll need it (plus your Pro code) to restore " +
                    "on a new device. Write it down and store it offline — anyone with it can read your backup.",
                fontSize = 13.sp, fontFamily = DMSans, color = MaterialTheme.colors.secondary, textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            Box(Modifier.fillMaxWidth()) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp), elevation = 0.dp,
                    backgroundColor = MaterialTheme.colors.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
                ) {
                    Column(Modifier.padding(14.dp)) {
                        words.chunked(2).forEachIndexed { rowIdx, pair ->
                            Row(Modifier.fillMaxWidth()) {
                                pair.forEachIndexed { colIdx, w ->
                                    val number = rowIdx * 2 + colIdx + 1
                                    Row(
                                        Modifier.weight(1f).padding(vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "$number.", fontSize = 13.sp, fontFamily = DMSans,
                                            color = MaterialTheme.colors.secondary,
                                            modifier = Modifier.width(26.dp)
                                        )
                                        Text(w, fontSize = 14.sp, fontFamily = DMSans, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.onBackground)
                                    }
                                }
                            }
                        }
                    }
                }
                if (!revealed) {
                    Box(
                        Modifier.matchParentSize().clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colors.surface.copy(alpha = 0.98f))
                            .clickable { revealed = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Visibility, null, Modifier.size(28.dp), tint = MaterialTheme.colors.primary)
                            Spacer(Modifier.height(8.dp))
                            Text("Tap to reveal", fontFamily = DMSans, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.primary)
                            Text("Make sure no one is watching", fontSize = 12.sp, fontFamily = DMSans, color = MaterialTheme.colors.secondary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(phrase))
                    copied = true
                },
                enabled = revealed,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp), tint = if (revealed) MaterialTheme.colors.primary else MaterialTheme.colors.secondary)
                Spacer(Modifier.width(8.dp))
                Text(if (copied) "Copied to clipboard" else "Copy to clipboard", fontFamily = DMSans, fontWeight = FontWeight.SemiBold, color = if (revealed) MaterialTheme.colors.primary else MaterialTheme.colors.secondary)
            }

            Spacer(Modifier.height(8.dp))

            if (isFirstTime) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { confirmed = !confirmed }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = confirmed,
                        onCheckedChange = { confirmed = it },
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colors.primary)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("I've saved my recovery phrase somewhere safe", fontSize = 13.sp, fontFamily = DMSans, color = MaterialTheme.colors.onBackground)
                }
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = onDone,
                enabled = !isFirstTime || (revealed && confirmed),
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary),
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.elevation(0.dp, 0.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(if (isFirstTime) "Continue" else "Done", color = MaterialTheme.colors.onPrimary, fontFamily = DMSans, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ═══════════════ ENTER RECOVERY PHRASE (RESTORE) ═══════════════

@Composable
private fun EnterPhraseScreen(onConfirmed: (String) -> Unit, onBack: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val wordCount = remember(text) { text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        BackupTopBar("Enter Recovery Phrase", onBack = onBack)

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colors.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.VpnKey, null, Modifier.size(32.dp), tint = MaterialTheme.colors.primary) }

            Spacer(Modifier.height(16.dp))
            Text(
                "Enter Your 24-Word Phrase",
                fontSize = 19.sp, fontFamily = DMSans, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onBackground, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Type or paste the 24-word recovery phrase you saved when you enabled backup. " +
                    "Separate each word with a space.",
                fontSize = 13.sp, fontFamily = DMSans, color = MaterialTheme.colors.secondary, textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { text = it; error = null },
                label = { Text("Recovery phrase", fontFamily = DMSans) },
                isError = error != null,
                modifier = Modifier.fillMaxWidth().height(140.dp)
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    error ?: "$wordCount / 24 words",
                    fontSize = 12.sp, fontFamily = DMSans,
                    color = if (error != null) MaterialTheme.colors.error else MaterialTheme.colors.secondary
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    if (VaultBackup.validateBackupPassphrase(text)) onConfirmed(text.trim())
                    else error = "That doesn't look like a valid 24-word phrase. Check the words and spelling."
                },
                enabled = wordCount == 24,
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary),
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.elevation(0.dp, 0.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Filled.CloudDownload, null, tint = MaterialTheme.colors.onPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Restore Backup", color = MaterialTheme.colors.onPrimary, fontFamily = DMSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }

            Spacer(Modifier.height(24.dp))
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
private fun StorageUsageCard(usage: VaultStorageUsage) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp), elevation = 1.dp, backgroundColor = MaterialTheme.colors.surface
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Storage, null, Modifier.size(18.dp), tint = MaterialTheme.colors.primary)
                Spacer(Modifier.width(8.dp))
                Text("Cloud Storage", fontSize = 14.sp, fontFamily = DMSans, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.onBackground)
                Spacer(Modifier.weight(1f))
                Text(
                    "${formatStorageGb(usage.usedBytes)}/${formatStorageGb(usage.quotaBytes)}",
                    fontSize = 12.sp, fontFamily = DMSans, fontWeight = FontWeight.Medium, color = MaterialTheme.colors.secondary
                )
            }
            Spacer(Modifier.height(12.dp))
            // Usage bar — turns amber when nearly full.
            val nearlyFull = usage.fraction >= 0.9f
            LinearProgressIndicator(
                progress = usage.fraction,
                color = if (nearlyFull) AmberBadge else MaterialTheme.colors.primary,
                backgroundColor = MaterialTheme.colors.onBackground.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${formatStorageGb(usage.availableBytes)} available",
                fontSize = 12.sp, fontFamily = DMSans, color = MaterialTheme.colors.secondary
            )
        }
    }
}

@Composable
private fun DevicesCard(devices: List<chat.simplex.common.views.chatlist.DexgramDevice>) {
    val activeCount = devices.count { it.isActive }
    // The active device is almost certainly this phone (Pro allows one), so we
    // can fill in the real model/name when the backend didn't store them.
    val thisDevice = remember { currentDeviceLabel() }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp), elevation = 1.dp, backgroundColor = MaterialTheme.colors.surface
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Devices, null, Modifier.size(18.dp), tint = MaterialTheme.colors.primary)
                Spacer(Modifier.width(8.dp))
                Text("Devices", fontSize = 14.sp, fontFamily = DMSans, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.onBackground)
                Spacer(Modifier.weight(1f))
                Text(
                    "$activeCount active",
                    fontSize = 12.sp, fontFamily = DMSans, fontWeight = FontWeight.Medium,
                    color = if (activeCount > 1) AmberBadge else GreenBadge
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (activeCount > 1)
                    "Pro allows 1 active device at a time. Sign out on other devices to continue here."
                else
                    "Pro allows 1 active device at a time.",
                fontSize = 12.sp, fontFamily = DMSans, color = MaterialTheme.colors.secondary
            )
            if (devices.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Divider(color = MaterialTheme.colors.onBackground.copy(alpha = 0.08f))
                devices.forEach { d ->
                    // Name (user-set) and model, falling back to this phone's real
                    // values when the active device has none from the backend.
                    val apiName = listOf(d.name, d.deviceName).firstOrNull { it.isNotBlank() } ?: ""
                    val apiModel = d.model
                    val name = apiName.ifBlank { if (d.isActive) thisDevice.name else "" }
                    val model = apiModel.ifBlank { if (d.isActive) thisDevice.model else "" }
                    val title = name.ifBlank { model }.ifBlank { if (d.isActive) "This device" else d.platform.ifBlank { "Device" } }
                    val subtitle = listOf(
                        model.takeIf { it.isNotBlank() && it != title },
                        d.platform.takeIf { it.isNotBlank() }
                    ).filterNotNull().joinToString(" · ")

                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(8.dp).clip(CircleShape)
                                .background(if (d.isActive) GreenBadge else MaterialTheme.colors.onBackground.copy(alpha = 0.25f))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                title,
                                fontSize = 13.sp, fontFamily = DMSans, fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colors.onBackground,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            if (subtitle.isNotBlank()) {
                                Text(
                                    subtitle,
                                    fontSize = 11.sp, fontFamily = DMSans, color = MaterialTheme.colors.secondary,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (d.isActive) "Active" else "Inactive",
                            fontSize = 11.sp, fontFamily = DMSans,
                            color = if (d.isActive) GreenBadge else MaterialTheme.colors.secondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Storage figures always in GB, e.g. "0.04GB" or "5GB". Whole numbers drop the
 * decimals (quotas like "5GB"); fractional usage shows 2 decimals ("0.04GB").
 */
private fun formatStorageGb(b: Long): String {
    val gb = b / (1024.0 * 1024 * 1024)
    val text = if (gb == kotlin.math.floor(gb)) gb.toLong().toString() else "%.2f".format(gb)
    return "${text}GB"
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
