package com.Pinak.ProductiveClub.ui.friends

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.Pinak.ProductiveClub.util.AuthManager
import com.Pinak.ProductiveClub.ui.home.FriendProofDialog
import com.Pinak.ProductiveClub.util.FriendManager
import com.Pinak.ProductiveClub.util.FriendStatus
import com.Pinak.ProductiveClub.util.GroupInfo
import com.Pinak.ProductiveClub.util.GroupMemberProgress
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    group: GroupInfo,
    onBack: () -> Unit,
    onGroupDeleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUid = AuthManager.currentUserId ?: ""

    var currentGroup by remember(group.id) { mutableStateOf(group) }
    var membersProgress by remember { mutableStateOf<List<GroupMemberProgress>>(emptyList()) }
    var isLoadingMembers by remember { mutableStateOf(true) }

    // Dialog & Picker States
    var selectedMemberForProof by remember { mutableStateOf<FriendStatus?>(null) }
    var showAddFriendsPicker by remember { mutableStateOf(false) }
    var selectedFriendsToAdd by remember { mutableStateOf(setOf<String>()) }
    var isAddingFriends by remember { mutableStateOf(false) }

    // Owner Governance Dialog States
    var showOwnerControlsSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }
    var showRegenCodeConfirm by remember { mutableStateOf(false) }
    var memberToKick by remember { mutableStateOf<GroupMemberProgress?>(null) }
    var memberToMakeOwner by remember { mutableStateOf<GroupMemberProgress?>(null) }
    var showDeleteGroupConfirm by remember { mutableStateOf(false) }
    var showLeaveGroupConfirm by remember { mutableStateOf(false) }

    val friendStatuses by FriendManager.friendStatuses.collectAsState()
    val isOwner = currentGroup.creatorUid == currentUid

    // Function to reload progress
    fun reloadProgress() {
        scope.launch {
            isLoadingMembers = true
            val updated = FriendManager.fetchGroupMembersWithProgress(
                currentGroup.id,
                currentGroup.memberUids,
                currentGroup.creatorUid
            )
            membersProgress = updated
            // Check group streak under 75% rule
            val refreshedGroup = FriendManager.checkAndUpdateGroupStreak(currentGroup.id)
            if (refreshedGroup != null) {
                currentGroup = refreshedGroup
            }
            isLoadingMembers = false
        }
    }

    LaunchedEffect(currentGroup.id, currentGroup.memberUids) {
        reloadProgress()
    }

    // Available mutual friends not in this group
    val availableFriends = remember(friendStatuses, currentGroup.memberUids) {
        friendStatuses.map { it.friend }.filter { it.uid !in currentGroup.memberUids }
    }

    // Proof Inspection Dialog
    selectedMemberForProof?.let { status ->
        FriendProofDialog(
            friendStatus = status,
            onDismiss = { selectedMemberForProof = null }
        )
    }

    // --- 1. RENAME GROUP DIALOG ---
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Group", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("New Group Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = renameInput.trim()
                        if (trimmed.isNotBlank()) {
                            scope.launch {
                                val res = FriendManager.renameGroup(currentGroup.id, trimmed)
                                res.fold(
                                    onSuccess = {
                                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                                        currentGroup = currentGroup.copy(name = trimmed)
                                        showRenameDialog = false
                                    },
                                    onFailure = {
                                        Toast.makeText(context, it.message ?: "Failed", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    },
                    enabled = renameInput.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    // --- 2. REGENERATE CODE CONFIRMATION ---
    if (showRegenCodeConfirm) {
        AlertDialog(
            onDismissRequest = { showRegenCodeConfirm = false },
            title = { Text("Regenerate Group Code", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure? This will immediately invalidate the current code (${currentGroup.groupCode}). Anyone with the old code will no longer be able to join.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRegenCodeConfirm = false
                        scope.launch {
                            val res = FriendManager.regenerateGroupCode(currentGroup.id, currentGroup.groupCode)
                            res.fold(
                                onSuccess = { newCode ->
                                    Toast.makeText(context, "New Group Code: $newCode", Toast.LENGTH_LONG).show()
                                    currentGroup = currentGroup.copy(groupCode = newCode)
                                },
                                onFailure = {
                                    Toast.makeText(context, it.message ?: "Failed", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                ) {
                    Text("Regenerate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegenCodeConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // --- 3. KICK MEMBER CONFIRMATION ---
    memberToKick?.let { target ->
        AlertDialog(
            onDismissRequest = { memberToKick = null },
            title = { Text("Remove Member", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to remove ${target.user.displayName} from \"${currentGroup.name}\"?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uidToRemove = target.user.uid
                        memberToKick = null
                        scope.launch {
                            val res = FriendManager.kickGroupMember(currentGroup.id, uidToRemove)
                            res.fold(
                                onSuccess = {
                                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                                    currentGroup = currentGroup.copy(memberUids = currentGroup.memberUids.filter { it != uidToRemove })
                                    reloadProgress()
                                },
                                onFailure = {
                                    Toast.makeText(context, it.message ?: "Failed", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToKick = null }) { Text("Cancel") }
            }
        )
    }

    // --- 4. TRANSFER OWNERSHIP CONFIRMATION ---
    memberToMakeOwner?.let { target ->
        AlertDialog(
            onDismissRequest = { memberToMakeOwner = null },
            title = { Text("Transfer Ownership", fontWeight = FontWeight.Bold) },
            text = {
                Text("Make ${target.user.displayName} the new owner of \"${currentGroup.name}\"? You will step down as group owner.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newOwnerUid = target.user.uid
                        memberToMakeOwner = null
                        scope.launch {
                            val res = FriendManager.transferGroupOwnership(currentGroup.id, newOwnerUid)
                            res.fold(
                                onSuccess = {
                                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                                    currentGroup = currentGroup.copy(creatorUid = newOwnerUid)
                                    reloadProgress()
                                },
                                onFailure = {
                                    Toast.makeText(context, it.message ?: "Failed", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                ) {
                    Text("Transfer")
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToMakeOwner = null }) { Text("Cancel") }
            }
        )
    }

    // --- 5. DELETE GROUP CONFIRMATION ---
    if (showDeleteGroupConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteGroupConfirm = false },
            title = { Text("Disband Group", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = {
                Text("Are you sure you want to permanently delete \"${currentGroup.name}\"? All members will be removed and this action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteGroupConfirm = false
                        scope.launch {
                            val res = FriendManager.deleteGroup(currentGroup.id)
                            res.fold(
                                onSuccess = {
                                    Toast.makeText(context, "Group deleted", Toast.LENGTH_SHORT).show()
                                    onGroupDeleted()
                                },
                                onFailure = {
                                    Toast.makeText(context, it.message ?: "Failed", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Group")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteGroupConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // --- 6. LEAVE GROUP CONFIRMATION (for non-owners) ---
    if (showLeaveGroupConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveGroupConfirm = false },
            title = { Text("Leave Group", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to leave \"${currentGroup.name}\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveGroupConfirm = false
                        scope.launch {
                            val res = FriendManager.leaveGroup(currentGroup.id)
                            res.fold(
                                onSuccess = {
                                    Toast.makeText(context, "Left group", Toast.LENGTH_SHORT).show()
                                    onGroupDeleted()
                                },
                                onFailure = {
                                    Toast.makeText(context, it.message ?: "Failed", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Leave")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveGroupConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // --- 7. ADD FRIENDS PICKER DIALOG ---
    if (showAddFriendsPicker) {
        AlertDialog(
            onDismissRequest = { showAddFriendsPicker = false },
            title = { Text("Invite Friends", fontWeight = FontWeight.Bold) },
            text = {
                if (availableFriends.isEmpty()) {
                    Text("All your mutual friends are already in this group!")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(availableFriends) { f ->
                            val isSelected = f.uid in selectedFriendsToAdd
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedFriendsToAdd = if (isSelected) {
                                            selectedFriendsToAdd - f.uid
                                        } else {
                                            selectedFriendsToAdd + f.uid
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(f.avatarEmoji, fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(f.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(f.friendCode, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            selectedFriendsToAdd = if (checked) selectedFriendsToAdd + f.uid else selectedFriendsToAdd - f.uid
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (availableFriends.isNotEmpty()) {
                    Button(
                        onClick = {
                            scope.launch {
                                isAddingFriends = true
                                val res = FriendManager.addMembersToGroup(currentGroup.id, selectedFriendsToAdd.toList())
                                isAddingFriends = false
                                res.fold(
                                    onSuccess = {
                                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                                        showAddFriendsPicker = false
                                        currentGroup = currentGroup.copy(memberUids = currentGroup.memberUids + selectedFriendsToAdd)
                                        selectedFriendsToAdd = emptySet()
                                        reloadProgress()
                                    },
                                    onFailure = {
                                        Toast.makeText(context, it.message ?: "Failed", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        },
                        enabled = !isAddingFriends && selectedFriendsToAdd.isNotEmpty()
                    ) {
                        Text("Add (${selectedFriendsToAdd.size})")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFriendsPicker = false }) { Text("Cancel") }
            }
        )
    }

    // --- 8. OWNER CONTROLS BOTTOM SHEET ---
    if (showOwnerControlsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOwnerControlsSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("👑", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Owner Controls", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Text("You are the owner of this group. Manage group settings and membership below.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Action: Rename Group
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showOwnerControlsSheet = false
                            renameInput = currentGroup.name
                            showRenameDialog = true
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Rename Group", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("Change the name displayed to all members", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Action: Regenerate Code
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showOwnerControlsSheet = false
                            showRegenCodeConfirm = true
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Regenerate Group Code", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("Invalidate old code and generate a fresh invite code", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Action: Disband Group (Destructive)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showOwnerControlsSheet = false
                            showDeleteGroupConfirm = true
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Disband / Delete Group", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            Text("Permanently delete this group and remove all members", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // --- MAIN SCREEN SCAFFOLD ---
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            currentGroup.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${membersProgress.size} members",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isOwner) {
                        IconButton(onClick = { showOwnerControlsSheet = true }) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = "Owner Settings", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Group Code", currentGroup.groupCode))
                            Toast.makeText(context, "Copied code: ${currentGroup.groupCode}", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Code")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. 75% STREAK PROGRESS & HEALTH CARD
            item {
                val totalMembers = max(1, membersProgress.size)
                val submittedCount = membersProgress.count { it.todaySummary != null }
                val ratio = submittedCount.toDouble() / totalMembers.toDouble()
                val targetThreshold = 0.75
                val minNeeded = ceil(totalMembers * targetThreshold).toInt()
                val isThresholdMet = ratio >= targetThreshold

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isThresholdMet) {
                            Color(0xFF1B5E20).copy(alpha = 0.15f)
                        } else {
                            Color(0xFFE65100).copy(alpha = 0.15f)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Whatshot, contentDescription = null, tint = Color(0xFFFF5722), modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "${currentGroup.streak} Day Group Streak",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isThresholdMet) Color(0xFF4CAF50).copy(alpha = 0.2f) else Color(0xFFFF9800).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    if (isThresholdMet) "Protected 🔥" else "Pending ⚠️",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isThresholdMet) Color(0xFF2E7D32) else Color(0xFFE65100),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Progress bar towards 75%
                        val progressFraction = (submittedCount.toFloat() / totalMembers.toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (isThresholdMet) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$submittedCount of $totalMembers uploaded today (${(ratio * 100).toInt()}%)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "75% required",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = if (isThresholdMet) {
                                "Awesome! 75% or more of members have submitted proofs. Group streak is safely maintained for today!"
                            } else {
                                "Need ${minNeeded - submittedCount} more member(s) to upload before midnight to preserve the group streak!"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 2. GROUP CODE & INVITE BANNER
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "GROUP CODE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    currentGroup.groupCode.ifEmpty { "Generating..." },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("Group Code", currentGroup.groupCode))
                                        Toast.makeText(context, "Copied: ${currentGroup.groupCode}", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy", maxLines = 1)
                                }

                                if (availableFriends.isNotEmpty()) {
                                    FilledTonalButton(
                                        onClick = { showAddFriendsPicker = true },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Invite", maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. ACCOUNTABILITY PROGRESS FEED TITLE
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Member Progress Feed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { reloadProgress() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                    }
                }
            }

            // 4. MEMBER PROGRESS CARDS
            if (isLoadingMembers) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
            } else {
                items(membersProgress) { progress ->
                    val user = progress.user
                    val summary = progress.todaySummary
                    val isSubmitted = summary != null
                    var showMemberOptions by remember { mutableStateOf(false) }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isSubmitted) {
                                    selectedMemberForProof = FriendStatus(friend = user, summary = summary)
                                } else {
                                    Toast.makeText(context, "${user.displayName} has not submitted proof today yet", Toast.LENGTH_SHORT).show()
                                }
                            }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Member Header: Avatar, Name, Owner Badge, Streak, Options
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(user.avatarEmoji, fontSize = 22.sp)
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            user.displayName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (progress.isOwner) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFFFFD54F).copy(alpha = 0.35f)
                                            ) {
                                                Text(
                                                    "👑 Owner",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFF57F17),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            "🔥 ${user.currentStreak}d streak",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (user.todayScreenTime > 0) {
                                            Text(
                                                "• ⏱️ ${user.todayScreenTime}m",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                // Status Badge (Submitted / Pending)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSubmitted) Color(0xFF4CAF50).copy(alpha = 0.18f) else Color(0xFFFF9800).copy(alpha = 0.18f)
                                ) {
                                    Text(
                                        if (isSubmitted) "Submitted ✅" else "Pending ⏳",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSubmitted) Color(0xFF2E7D32) else Color(0xFFE65100),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }

                                // Owner Action Menu for other members
                                if (isOwner && !progress.isOwner) {
                                    Box {
                                        IconButton(onClick = { showMemberOptions = true }) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "Options")
                                        }
                                        DropdownMenu(
                                            expanded = showMemberOptions,
                                            onDismissRequest = { showMemberOptions = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Transfer Ownership") },
                                                onClick = {
                                                    showMemberOptions = false
                                                    memberToMakeOwner = progress
                                                },
                                                leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Remove from Group", color = MaterialTheme.colorScheme.error) },
                                                onClick = {
                                                    showMemberOptions = false
                                                    memberToKick = progress
                                                },
                                                leadingIcon = { Icon(Icons.Default.PersonRemove, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                                            )
                                        }
                                    }
                                }
                            }

                            // If Submitted: Show Proof Preview Thumbnail & Notes
                            if (isSubmitted) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val photos = summary.photoUrls
                                    if (photos.isNotEmpty()) {
                                        AsyncImage(
                                            model = photos.first(),
                                            contentDescription = "Proof Photo",
                                            modifier = Modifier
                                                .size(60.dp)
                                                .clip(RoundedCornerShape(10.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        if (summary.text.isNotBlank()) {
                                            Text(
                                                summary.text,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            "Tap to inspect proof & tasks",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. BOTTOM ACTIONS: LEAVE GROUP (For non-owners)
            if (!isOwner) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(
                            onClick = { showLeaveGroupConfirm = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Leave Group")
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            } else {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
