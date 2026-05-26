package chat.simplex.common.views.vault

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.ChatController
import chat.simplex.common.platform.BackHandler
import chat.simplex.common.ui.theme.DMSans
import chat.simplex.common.views.helpers.LAResult
import chat.simplex.common.views.helpers.authenticate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BgPrimary @Composable get() = MaterialTheme.colors.background
private val CardBg @Composable get() = MaterialTheme.colors.surface
private val AccentBlue @Composable get() = MaterialTheme.colors.primary
private val TextPrimary @Composable get() = MaterialTheme.colors.onBackground
private val TextSecondary @Composable get() = MaterialTheme.colors.secondary
private val DangerRed @Composable get() = MaterialTheme.colors.error
private val GreenBadge = Color(0xFF4CAF50)

private sealed class VScreen {
    object Locked : VScreen()
    object Home : VScreen()
    data class Folder(val folder: VaultFolder, val password: String? = null) : VScreen()
    data class Preview(val entry: VaultFileEntry, val folderPassword: String? = null) : VScreen()
    object BackupSettings : VScreen()
}

@Composable
fun VaultView(close: () -> Unit) {
    var screen by remember { mutableStateOf<VScreen>(VScreen.Locked) }
    var index by remember { mutableStateOf(VaultIndex()) }
    val scope = rememberCoroutineScope()

    fun reload() { scope.launch { index = withContext(Dispatchers.IO) { VaultStorage.getIndex() } } }

    // Back navigation
    BackHandler(enabled = screen is VScreen.Preview) {
        val prev = screen as VScreen.Preview
        val folder = index.folders.find { it.id == prev.entry.folderId }
        screen = if (folder != null) VScreen.Folder(folder, prev.folderPassword) else VScreen.Home
    }
    BackHandler(enabled = screen is VScreen.Folder) { screen = VScreen.Home }
    BackHandler(enabled = screen == VScreen.BackupSettings) { screen = VScreen.Home }
    BackHandler(enabled = screen == VScreen.Home) { close() }
    BackHandler(enabled = screen == VScreen.Locked) { close() }

    // Authenticate on entry
    LaunchedEffect(Unit) {
        if (ChatController.appPrefs.performLA.get()) {
            authenticate(
                promptTitle = "Unlock Vault",
                promptSubtitle = "Authenticate to access your secure vault",
                oneTime = true
            ) { result ->
                when (result) {
                    is LAResult.Success -> { screen = VScreen.Home; reload() }
                    else -> close()
                }
            }
        } else {
            screen = VScreen.Home; reload()
        }
    }

    when (val s = screen) {
        VScreen.Locked -> LockedScreen()
        VScreen.Home -> HomeScreen(
            index = index,
            onOpenFolder = { folder ->
                if (folder.hasPassword) {
                    screen = VScreen.Locked
                } else {
                    screen = VScreen.Folder(folder, null)
                }
            },
            onFolderVerified = { folder, pw -> screen = VScreen.Folder(folder, pw) },
            onFileClick = { entry -> screen = VScreen.Preview(entry) },
            onRefresh = { reload() },
            onBack = close,
            onBackupSettings = { screen = VScreen.BackupSettings }
        )
        is VScreen.Folder -> FolderScreen(
            folder = s.folder,
            folderPassword = s.password,
            files = index.files.filter { it.folderId == s.folder.id },
            onFileClick = { entry -> screen = VScreen.Preview(entry, s.password) },
            onRefresh = { reload() },
            onBack = { screen = VScreen.Home }
        )
        is VScreen.Preview -> PreviewScreen(
            entry = s.entry,
            folderPassword = s.folderPassword,
            onBack = {
                val folder = index.folders.find { it.id == s.entry.folderId }
                screen = if (folder != null) VScreen.Folder(folder, s.folderPassword) else VScreen.Home
            },
            onDeleted = {
                val folder = index.folders.find { it.id == s.entry.folderId }
                screen = if (folder != null) VScreen.Folder(folder, s.folderPassword) else VScreen.Home
                reload()
            }
        )
        VScreen.BackupSettings -> BackupSettingsView(onBack = { screen = VScreen.Home; reload() })
    }
}

// ═══════════════ LOCKED ═══════════════

@Composable
private fun LockedScreen() {
    Box(Modifier.fillMaxSize().background(BgPrimary), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Lock, null, Modifier.size(64.dp), tint = AccentBlue)
            Spacer(Modifier.height(16.dp))
            Text("Vault Locked", fontSize = 20.sp, fontFamily = DMSans, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text("Authenticating...", fontSize = 14.sp, fontFamily = DMSans, color = TextSecondary)
        }
    }
}

// ═══════════════ HOME ═══════════════

@Composable
private fun HomeScreen(
    index: VaultIndex,
    onOpenFolder: (VaultFolder) -> Unit,
    onFolderVerified: (VaultFolder, String?) -> Unit,
    onFileClick: (VaultFileEntry) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onBackupSettings: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val folders = index.folders
    val looseFiles = index.files.filter { it.folderId == null }
    var showCreateFolder by remember { mutableStateOf(false) }
    var showFilePicker by remember { mutableStateOf(false) }
    var passwordPrompt by remember { mutableStateOf<VaultFolder?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }
    var deleteFolder by remember { mutableStateOf<VaultFolder?>(null) }
    var deleteFolderAuthPending by remember { mutableStateOf<VaultFolder?>(null) }
    var deleteFolderPasswordPending by remember { mutableStateOf<VaultFolder?>(null) }
    var deleteFile by remember { mutableStateOf<VaultFileEntry?>(null) }

    val backupEnabled = remember { mutableStateOf(VaultBackup.isBackupEnabled()) }
    val isPremiumUser = ChatController.appPrefs.premiumActive.state
    LaunchedEffect(Unit) { backupEnabled.value = VaultBackup.isBackupEnabled() }

    Box(Modifier.fillMaxSize().background(BgPrimary)) {
        Column(Modifier.fillMaxSize()) {
            VaultTopBar(
                "Secure Vault",
                onBack = onBack,
                trailing = {
                    // Cloud backup is a Pro-tier feature — gate the entry point.
                    IconButton(onClick = {
                        chat.simplex.common.views.chatlist.PremiumGate.requirePremium {
                            onBackupSettings()
                        }
                    }) {
                        Box(contentAlignment = Alignment.Center) {
                            val iconTint = when {
                                !isPremiumUser.value -> MaterialTheme.colors.secondary
                                backupEnabled.value -> GreenBadge
                                else -> AccentBlue
                            }
                            val iconVec = when {
                                !isPremiumUser.value -> Icons.Filled.Lock
                                backupEnabled.value -> Icons.Filled.CloudDone
                                else -> Icons.Filled.CloudUpload
                            }
                            Icon(iconVec, "Cloud Backup", Modifier.size(22.dp), tint = iconTint)
                        }
                    }
                }
            )

            if (folders.isEmpty() && looseFiles.isEmpty()) {
                // Empty state
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Filled.Shield, null, Modifier.size(80.dp), tint = MaterialTheme.colors.secondary)
                        Spacer(Modifier.height(16.dp))
                        Text("Your vault is empty", fontSize = 18.sp, fontFamily = DMSans, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        Text("Create folders and add files.\nEverything is encrypted with AES-256-GCM\nand hardware-backed keys.", fontSize = 13.sp, fontFamily = DMSans, color = TextSecondary, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(32.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { showCreateFolder = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Icon(Icons.Filled.CreateNewFolder, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("New Folder", fontFamily = DMSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                            Button(
                                onClick = { showFilePicker = true },
                                colors = ButtonDefaults.buttonColors(backgroundColor = AccentBlue),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Icon(Icons.Filled.Add, null, tint = MaterialTheme.colors.onPrimary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add File", color = MaterialTheme.colors.onPrimary, fontFamily = DMSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Folders section
                    if (folders.isNotEmpty()) {
                        item {
                            Text("Folders", fontSize = 14.sp, fontFamily = DMSans, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                            Spacer(Modifier.height(8.dp))
                        }
                        items(folders, key = { it.id }) { folder ->
                            val count = index.files.count { it.folderId == folder.id }
                            FolderCard(
                                folder = folder,
                                fileCount = count,
                                onClick = {
                                    if (folder.hasPassword) passwordPrompt = folder
                                    else onFolderVerified(folder, null)
                                },
                                onDelete = { deleteFolderAuthPending = folder }
                            )
                        }
                    }
                    // Loose files section
                    if (looseFiles.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text("Files", fontSize = 14.sp, fontFamily = DMSans, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                            Spacer(Modifier.height(8.dp))
                        }
                        items(looseFiles, key = { it.id }) { entry ->
                            FileCard(entry, onClick = { onFileClick(entry) }, onDelete = { deleteFile = entry })
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        // FAB with menu
        if (folders.isNotEmpty() || looseFiles.isNotEmpty()) {
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 90.dp)) {
                FloatingActionButton(onClick = { showAddMenu = true }, backgroundColor = AccentBlue) {
                    Icon(Icons.Filled.Add, "Add", tint = MaterialTheme.colors.onPrimary)
                }
                DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                    DropdownMenuItem(onClick = { showAddMenu = false; showCreateFolder = true }) {
                        Icon(Icons.Filled.CreateNewFolder, null, Modifier.size(20.dp), tint = TextPrimary)
                        Spacer(Modifier.width(12.dp))
                        Text("New Folder", fontFamily = DMSans)
                    }
                    DropdownMenuItem(onClick = { showAddMenu = false; showFilePicker = true }) {
                        Icon(Icons.Filled.NoteAdd, null, Modifier.size(20.dp), tint = TextPrimary)
                        Spacer(Modifier.width(12.dp))
                        Text("Add File", fontFamily = DMSans)
                    }
                }
            }
        }
    }

    // Dialogs
    if (showCreateFolder) {
        CreateFolderDialog(
            onDismiss = { showCreateFolder = false },
            onCreate = { name, pw ->
                showCreateFolder = false
                scope.launch { withContext(Dispatchers.IO) { VaultStorage.createFolder(name, pw) }; onRefresh() }
            }
        )
    }

    passwordPrompt?.let { folder ->
        FolderPasswordDialog(
            folderName = folder.name,
            onDismiss = { passwordPrompt = null },
            onSubmit = { pw ->
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { VaultStorage.verifyFolderPassword(folder.id, pw) }
                    if (ok) { passwordPrompt = null; onFolderVerified(folder, pw) }
                }
            }
        )
    }

    // Step 1: App lock authentication before folder delete
    deleteFolderAuthPending?.let { folder ->
        LaunchedEffect(folder.id) {
            if (ChatController.appPrefs.performLA.get()) {
                authenticate(
                    promptTitle = "Delete Folder",
                    promptSubtitle = "Authenticate to delete \"${folder.name}\"",
                    oneTime = true
                ) { result ->
                    deleteFolderAuthPending = null
                    when (result) {
                        is LAResult.Success -> {
                            if (folder.hasPassword) deleteFolderPasswordPending = folder
                            else deleteFolder = folder
                        }
                        else -> {}
                    }
                }
            } else {
                deleteFolderAuthPending = null
                if (folder.hasPassword) deleteFolderPasswordPending = folder
                else deleteFolder = folder
            }
        }
    }

    // Step 2: Folder password verification (for password-protected folders)
    deleteFolderPasswordPending?.let { folder ->
        FolderPasswordDialog(
            folderName = folder.name,
            onDismiss = { deleteFolderPasswordPending = null },
            onSubmit = { pw ->
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { VaultStorage.verifyFolderPassword(folder.id, pw) }
                    if (ok) {
                        deleteFolderPasswordPending = null
                        deleteFolder = folder
                    }
                }
            }
        )
    }

    // Step 3: Final delete confirmation
    deleteFolder?.let { folder ->
        DeleteConfirmDialog(
            title = "Delete Folder",
            message = "\"${folder.name}\" and all its files will be permanently deleted. This cannot be undone.",
            onDismiss = { deleteFolder = null },
            onConfirm = {
                deleteFolder = null
                scope.launch { withContext(Dispatchers.IO) { VaultStorage.deleteFolder(folder.id) }; onRefresh() }
            }
        )
    }

    deleteFile?.let { entry ->
        DeleteConfirmDialog(
            title = "Delete File",
            message = "\"${entry.originalName}\" will be permanently deleted.",
            onDismiss = { deleteFile = null },
            onConfirm = {
                deleteFile = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        VaultStorage.deleteFile(entry)
                        if (entry.backedUp) VaultBackup.deleteBackup(entry.id)
                    }
                    onRefresh()
                }
            }
        )
    }

    if (showFilePicker) {
        VaultFilePicker(
            onFilePicked = { name, mime, bytes ->
                showFilePicker = false
                scope.launch {
                    val entry = withContext(Dispatchers.IO) { VaultStorage.importFile(name, mime, bytes, null, null) }
                    onRefresh()
                    if (entry != null && VaultBackup.isBackupEnabled()) {
                        withContext(Dispatchers.IO) { VaultBackup.backupFile(entry) }
                        onRefresh()
                    }
                }
            },
            onDismiss = { showFilePicker = false }
        )
    }
}

// ═══════════════ FOLDER CONTENTS ═══════════════

@Composable
private fun FolderScreen(
    folder: VaultFolder,
    folderPassword: String?,
    files: List<VaultFileEntry>,
    onFileClick: (VaultFileEntry) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showFilePicker by remember { mutableStateOf(false) }
    var deleteFile by remember { mutableStateOf<VaultFileEntry?>(null) }

    Column(Modifier.fillMaxSize().background(BgPrimary)) {
        VaultTopBar(
            title = folder.name,
            onBack = onBack,
            showLock = folder.hasPassword,
            subtitle = "${files.size} file${if (files.size != 1) "s" else ""}"
        )

        if (files.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.FolderOpen, null, Modifier.size(64.dp), tint = MaterialTheme.colors.secondary)
                    Spacer(Modifier.height(12.dp))
                    Text("Folder is empty", fontSize = 16.sp, fontFamily = DMSans, fontWeight = FontWeight.Medium, color = TextSecondary)
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { showFilePicker = true },
                        colors = ButtonDefaults.buttonColors(backgroundColor = AccentBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Icon(Icons.Filled.Add, null, tint = MaterialTheme.colors.onPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add File", color = MaterialTheme.colors.onPrimary, fontFamily = DMSans, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(files, key = { it.id }) { entry ->
                    FileCard(entry, onClick = { onFileClick(entry) }, onDelete = { deleteFile = entry })
                }
                item { Spacer(Modifier.height(80.dp)) }
            }

            // FAB
            Box(Modifier.fillMaxWidth()) {
                FloatingActionButton(
                    onClick = { showFilePicker = true },
                    backgroundColor = AccentBlue,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 24.dp)
                ) { Icon(Icons.Filled.Add, "Add", tint = MaterialTheme.colors.onPrimary) }
            }
        }
    }

    deleteFile?.let { entry ->
        DeleteConfirmDialog(
            title = "Delete File",
            message = "\"${entry.originalName}\" will be permanently deleted.",
            onDismiss = { deleteFile = null },
            onConfirm = {
                deleteFile = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        VaultStorage.deleteFile(entry)
                        if (entry.backedUp) VaultBackup.deleteBackup(entry.id)
                    }
                    onRefresh()
                }
            }
        )
    }

    if (showFilePicker) {
        VaultFilePicker(
            onFilePicked = { name, mime, bytes ->
                showFilePicker = false
                scope.launch {
                    val entry = withContext(Dispatchers.IO) { VaultStorage.importFile(name, mime, bytes, folder.id, folderPassword) }
                    onRefresh()
                    if (entry != null && VaultBackup.isBackupEnabled()) {
                        withContext(Dispatchers.IO) { VaultBackup.backupFileWithPassword(entry, folderPassword) }
                        onRefresh()
                    }
                }
            },
            onDismiss = { showFilePicker = false }
        )
    }
}

// ═══════════════ FILE PREVIEW ═══════════════

@Composable
private fun PreviewScreen(
    entry: VaultFileEntry,
    folderPassword: String?,
    onBack: () -> Unit,
    onDeleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showDelete by remember { mutableStateOf(false) }
    var showRestore by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var tempFilePath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(entry.id) {
        bytes = withContext(Dispatchers.IO) { VaultStorage.decryptFile(entry, folderPassword) }
        loading = false
    }

    val isPdf = entry.mimeType.contains("pdf", ignoreCase = true) ||
        entry.originalName.endsWith(".pdf", ignoreCase = true)

    val mediaPath = remember(bytes) {
        val data = bytes ?: return@remember null
        if (entry.fileType == VaultFileType.VIDEO || entry.fileType == VaultFileType.AUDIO) {
            val ext = when {
                entry.mimeType.contains("mp4") -> "mp4"
                entry.mimeType.contains("webm") -> "webm"
                entry.mimeType.contains("3gp") -> "3gp"
                entry.mimeType.contains("mkv") -> "mkv"
                entry.mimeType.contains("mp3") -> "mp3"
                entry.mimeType.contains("ogg") -> "ogg"
                entry.mimeType.contains("wav") -> "wav"
                entry.fileType == VaultFileType.VIDEO -> "mp4"
                else -> "mp3"
            }
            writeVaultTempFile(data, ext)?.also { tempFilePath = it }
        } else if (isPdf) {
            writeVaultTempFile(data, "pdf")?.also { tempFilePath = it }
        } else null
    }

    DisposableEffect(Unit) {
        onDispose {
            tempFilePath?.let { cleanupVaultTempFile(it) }
        }
    }

    fun doBack() {
        tempFilePath?.let { cleanupVaultTempFile(it); tempFilePath = null }
        onBack()
    }

    val isFullMedia = entry.fileType == VaultFileType.VIDEO

    Column(Modifier.fillMaxSize().background(if (isFullMedia) Color.Black else BgPrimary)) {
        if (!isFullMedia) {
            Surface(elevation = 2.dp, color = CardBg) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { doBack() }) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(entry.originalName, fontSize = 16.sp, fontFamily = DMSans, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${formatSize(entry.sizeBytes)} · ${entry.fileType.name}", fontSize = 12.sp, fontFamily = DMSans, color = TextSecondary)
                    }
                    IconButton(onClick = { showRestore = true }) {
                        Icon(Icons.Filled.SaveAlt, "Restore to Phone", tint = AccentBlue)
                    }
                    IconButton(onClick = { showDelete = true }) {
                        Icon(Icons.Filled.Delete, "Delete", tint = DangerRed)
                    }
                }
            }
        }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(color = AccentBlue)
            } else if (bytes != null) {
                val data = bytes!!
                val imageBmp = remember(data) {
                    if (entry.fileType == VaultFileType.PHOTO) decodeVaultImage(data) else null
                }

                when {
                    imageBmp != null -> {
                        Image(
                            bitmap = imageBmp,
                            contentDescription = entry.originalName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    mediaPath != null && entry.fileType == VaultFileType.VIDEO -> {
                        VaultMediaPlayer(filePath = mediaPath, isVideo = true)
                    }
                    mediaPath != null && entry.fileType == VaultFileType.AUDIO -> {
                        Column(
                            Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                Modifier.size(100.dp).clip(CircleShape).background(Color(0xFFFF9800).copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.MusicNote, null, Modifier.size(48.dp), tint = Color(0xFFFF9800))
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(entry.originalName, fontSize = 16.sp, fontFamily = DMSans, fontWeight = FontWeight.SemiBold, color = TextPrimary, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(4.dp))
                            Text(formatSize(entry.sizeBytes), fontSize = 13.sp, fontFamily = DMSans, color = TextSecondary)
                            Spacer(Modifier.height(24.dp))
                            VaultMediaPlayer(filePath = mediaPath, isVideo = false)
                        }
                    }
                    isPdf && mediaPath != null -> {
                        VaultPdfViewer(
                            filePath = mediaPath,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(100.dp).clip(CircleShape).background(fileTypeColor(entry.fileType).copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(fileTypeIcon(entry.fileType), null, Modifier.size(48.dp), tint = fileTypeColor(entry.fileType))
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(entry.originalName, fontSize = 16.sp, fontFamily = DMSans, fontWeight = FontWeight.SemiBold, color = TextPrimary, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(4.dp))
                            Text(formatSize(entry.sizeBytes) + " · " + formatDate(entry.addedAtMs), fontSize = 13.sp, fontFamily = DMSans, color = TextSecondary)
                        }
                    }
                }

                if (isFullMedia) {
                    Row(
                        Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { doBack() }) {
                            Icon(Icons.Filled.ArrowBack, "Back", Modifier.size(24.dp), tint = Color.White)
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(entry.originalName, fontSize = 14.sp, fontFamily = DMSans, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton(onClick = { showRestore = true }) {
                            Icon(Icons.Filled.SaveAlt, "Restore", Modifier.size(22.dp), tint = Color.White.copy(alpha = 0.8f))
                        }
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Filled.Delete, "Delete", Modifier.size(22.dp), tint = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
            } else {
                Text("Failed to decrypt file", fontSize = 16.sp, fontFamily = DMSans, color = DangerRed)
            }
        }
    }

    if (showRestore) {
        AlertDialog(
            onDismissRequest = { if (!restoring) showRestore = false },
            title = { Text("Restore to Phone", fontFamily = DMSans, fontWeight = FontWeight.Bold) },
            text = {
                if (restoring) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = AccentBlue, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Saving to phone and cleaning up...", fontFamily = DMSans, fontSize = 14.sp, color = TextSecondary)
                    }
                } else {
                    Text(
                        "\"${entry.originalName}\" will be saved back to your phone storage. " +
                            "It will be removed from the vault" +
                            if (entry.backedUp) " and from cloud backup." else ".",
                        fontFamily = DMSans, fontSize = 14.sp
                    )
                }
            },
            confirmButton = {
                if (!restoring) {
                    TextButton(onClick = {
                        restoring = true
                        scope.launch {
                            val data = bytes
                            if (data != null) {
                                val saved = withContext(Dispatchers.IO) {
                                    restoreFileToDevice(data, entry.originalName, entry.mimeType)
                                }
                                if (saved) {
                                    withContext(Dispatchers.IO) {
                                        VaultStorage.deleteFile(entry)
                                        if (entry.backedUp) VaultBackup.deleteBackup(entry.id)
                                    }
                                    tempFilePath?.let { cleanupVaultTempFile(it); tempFilePath = null }
                                    showRestore = false
                                    restoring = false
                                    onDeleted()
                                } else {
                                    restoring = false
                                }
                            } else {
                                restoring = false
                            }
                        }
                    }) {
                        Text("Restore", fontFamily = DMSans, fontWeight = FontWeight.Bold, color = AccentBlue)
                    }
                }
            },
            dismissButton = {
                if (!restoring) {
                    TextButton(onClick = { showRestore = false }) {
                        Text("Cancel", fontFamily = DMSans, color = TextSecondary)
                    }
                }
            }
        )
    }

    if (showDelete) {
        DeleteConfirmDialog(
            title = "Delete File",
            message = "\"${entry.originalName}\" will be permanently deleted.",
            onDismiss = { showDelete = false },
            onConfirm = {
                showDelete = false
                tempFilePath?.let { cleanupVaultTempFile(it); tempFilePath = null }
                scope.launch {
                    withContext(Dispatchers.IO) {
                        VaultStorage.deleteFile(entry)
                        if (entry.backedUp) VaultBackup.deleteBackup(entry.id)
                    }
                    onDeleted()
                }
            }
        )
    }
}

@Composable
expect fun VaultMediaPlayer(
    filePath: String,
    isVideo: Boolean
)

// ═══════════════ SHARED COMPONENTS ═══════════════

@Composable
private fun VaultTopBar(
    title: String,
    onBack: () -> Unit,
    showLock: Boolean = false,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Surface(elevation = 2.dp, color = CardBg) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary) }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 20.sp, fontFamily = DMSans, fontWeight = FontWeight.Bold, color = TextPrimary)
                if (subtitle != null) {
                    Text(subtitle, fontSize = 12.sp, fontFamily = DMSans, color = TextSecondary)
                }
            }
            if (showLock) {
                Icon(Icons.Filled.Lock, null, Modifier.size(16.dp), tint = Color(0xFFFF9800))
                Spacer(Modifier.width(4.dp))
            }
            trailing?.invoke()
            Icon(Icons.Filled.Lock, "Encrypted", Modifier.size(16.dp), tint = GreenBadge)
            Spacer(Modifier.width(4.dp))
            Text("AES-256", fontSize = 11.sp, fontFamily = DMSans, fontWeight = FontWeight.Medium, color = GreenBadge)
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun FolderCard(folder: VaultFolder, fileCount: Int, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp), elevation = 1.dp, backgroundColor = CardBg
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (folder.hasPassword) Color(0xFFFF9800).copy(alpha = 0.12f) else AccentBlue.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (folder.hasPassword) Icons.Filled.FolderSpecial else Icons.Filled.Folder,
                    null, Modifier.size(24.dp),
                    tint = if (folder.hasPassword) Color(0xFFFF9800) else AccentBlue
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(folder.name, fontSize = 15.sp, fontFamily = DMSans, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (folder.hasPassword) {
                        Icon(Icons.Filled.Lock, null, Modifier.size(12.dp), tint = Color(0xFFFF9800))
                        Spacer(Modifier.width(4.dp))
                        Text("Password protected", fontSize = 12.sp, fontFamily = DMSans, color = Color(0xFFFF9800))
                        Text(" · ", fontSize = 12.sp, color = TextSecondary)
                    }
                    Text("$fileCount file${if (fileCount != 1) "s" else ""}", fontSize = 12.sp, fontFamily = DMSans, color = TextSecondary)
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, "Delete", Modifier.size(18.dp), tint = MaterialTheme.colors.secondary)
            }
        }
    }
}

@Composable
private fun FileCard(entry: VaultFileEntry, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp), elevation = 1.dp, backgroundColor = CardBg
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(fileTypeColor(entry.fileType).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) { Icon(fileTypeIcon(entry.fileType), null, Modifier.size(20.dp), tint = fileTypeColor(entry.fileType)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.originalName, fontSize = 14.sp, fontFamily = DMSans, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(formatSize(entry.sizeBytes) + " · " + formatDate(entry.addedAtMs), fontSize = 12.sp, fontFamily = DMSans, color = TextSecondary)
            }
            if (entry.backedUp) {
                Icon(Icons.Filled.CloudDone, "Backed up", Modifier.size(14.dp), tint = AccentBlue)
                Spacer(Modifier.width(4.dp))
            }
            if (entry.isDoubleEncrypted) {
                Icon(Icons.Filled.EnhancedEncryption, null, Modifier.size(14.dp), tint = Color(0xFFFF9800))
                Spacer(Modifier.width(4.dp))
            }
            Icon(Icons.Filled.Lock, "Encrypted", Modifier.size(14.dp), tint = GreenBadge)
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, "Delete", Modifier.size(18.dp), tint = MaterialTheme.colors.secondary)
            }
        }
    }
}

// ═══════════════ DIALOGS ═══════════════

@Composable
private fun CreateFolderDialog(onDismiss: () -> Unit, onCreate: (String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var enablePassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirmPw by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Folder", fontFamily = DMSans, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder name", fontFamily = DMSans) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, null, Modifier.size(20.dp), tint = if (enablePassword) Color(0xFFFF9800) else TextSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text("Password protect", fontFamily = DMSans, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                    Switch(checked = enablePassword, onCheckedChange = { enablePassword = it }, colors = SwitchDefaults.colors(checkedThumbColor = AccentBlue))
                }
                if (enablePassword) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password", fontFamily = DMSans) },
                        singleLine = true,
                        visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPw = !showPw }) {
                                Icon(if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, tint = TextSecondary)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPw,
                        onValueChange = { confirmPw = it },
                        label = { Text("Confirm password", fontFamily = DMSans) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (enablePassword && password.isNotEmpty() && confirmPw.isNotEmpty() && password != confirmPw) {
                        Spacer(Modifier.height(4.dp))
                        Text("Passwords don't match", fontSize = 12.sp, fontFamily = DMSans, color = DangerRed)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "If you forget this password, files in this folder cannot be recovered.",
                        fontSize = 11.sp, fontFamily = DMSans, color = DangerRed.copy(alpha = 0.8f)
                    )
                }
            }
        },
        confirmButton = {
            val canCreate = name.isNotBlank() && (!enablePassword || (password.length >= 4 && password == confirmPw))
            TextButton(
                onClick = { onCreate(name.trim(), if (enablePassword) password else null) },
                enabled = canCreate
            ) {
                Text("Create", fontFamily = DMSans, fontWeight = FontWeight.Bold, color = if (canCreate) AccentBlue else TextSecondary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", fontFamily = DMSans, color = TextSecondary) }
        }
    )
}

@Composable
private fun FolderPasswordDialog(folderName: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, null, Modifier.size(20.dp), tint = Color(0xFFFF9800))
                Spacer(Modifier.width(8.dp))
                Text(folderName, fontFamily = DMSans, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text("Enter password to access this folder", fontSize = 13.sp, fontFamily = DMSans, color = TextSecondary)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = false },
                    label = { Text("Password", fontFamily = DMSans) },
                    singleLine = true,
                    visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                    isError = error,
                    trailingIcon = {
                        IconButton(onClick = { showPw = !showPw }) {
                            Icon(if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, tint = TextSecondary)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (error) {
                    Spacer(Modifier.height(4.dp))
                    Text("Wrong password", fontSize = 12.sp, fontFamily = DMSans, color = DangerRed)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (password.isNotEmpty()) {
                        error = true
                        onSubmit(password)
                    }
                },
                enabled = password.isNotEmpty()
            ) { Text("Unlock", fontFamily = DMSans, fontWeight = FontWeight.Bold, color = AccentBlue) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", fontFamily = DMSans, color = TextSecondary) }
        }
    )
}

@Composable
private fun DeleteConfirmDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = DMSans, fontWeight = FontWeight.Bold) },
        text = { Text(message, fontFamily = DMSans) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = DangerRed, fontFamily = DMSans, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary, fontFamily = DMSans) }
        }
    )
}

// ═══════════════ HELPERS ═══════════════

private fun fileTypeIcon(type: VaultFileType) = when (type) {
    VaultFileType.PHOTO -> Icons.Filled.Image
    VaultFileType.VIDEO -> Icons.Filled.PlayCircle
    VaultFileType.AUDIO -> Icons.Filled.MusicNote
    VaultFileType.DOCUMENT -> Icons.Filled.Description
    VaultFileType.OTHER -> Icons.Filled.InsertDriveFile
}

private fun fileTypeColor(type: VaultFileType) = when (type) {
    VaultFileType.PHOTO -> Color(0xFF4CAF50)
    VaultFileType.VIDEO -> Color(0xFF2196F3)
    VaultFileType.AUDIO -> Color(0xFFFF9800)
    VaultFileType.DOCUMENT -> Color(0xFF9C27B0)
    VaultFileType.OTHER -> Color(0xFF607D8B)
}

private fun formatSize(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${b / 1024} KB"
    b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / (1024.0 * 1024))
    else -> "%.2f GB".format(b / (1024.0 * 1024 * 1024))
}

private fun formatDate(ms: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(ms))
}
