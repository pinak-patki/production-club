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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.Pinak.ProductiveClub.util.FriendManager
import com.Pinak.ProductiveClub.util.FriendUser
import com.Pinak.ProductiveClub.util.GroupInfo
import kotlinx.coroutines.launch

@Composable
fun GroupDetailsDialog(
    group: GroupInfo,
    onDismiss: () -> Unit,
    onLeaveGroup: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val friendStatuses by FriendManager.friendStatuses.collectAsState()
    var members by remember { mutableStateOf<List<FriendUser>>(emptyList()) }
    var isLoadingMembers by remember { mutableStateOf(true) }

    var showAddFriendsPicker by remember { mutableStateOf(false) }
    var selectedFriendsToAdd by remember { mutableStateOf(setOf<String>()) }
    var isAddingFriends by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }

    // Fetch members on open
    LaunchedEffect(group.id, group.memberUids) {
        isLoadingMembers = true
        members = FriendManager.fetchGroupMemberDetails(group.memberUids)
        isLoadingMembers = false
    }

    // Available friends not currently in the group
    val availableFriends = remember(friendStatuses, group.memberUids) {
        friendStatuses.map { it.friend }.filter { it.uid !in group.memberUids }
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("Leave Group", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to leave \"${group.name}\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveConfirm = false
                        scope.launch {
                            val res = FriendManager.leaveGroup(group.id)
                            res.fold(
                                onSuccess = {
                                    Toast.makeText(context, "Left group", Toast.LENGTH_SHORT).show()
                                    onLeaveGroup()
                                },
                                onFailure = {
                                    Toast.makeText(context, it.message ?: "Failed to leave", Toast.LENGTH_SHORT).show()
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
                TextButton(onClick = { showLeaveConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Picker Dialog to add friends to this group
    if (showAddFriendsPicker) {
        AlertDialog(
            onDismissRequest = {
                showAddFriendsPicker = false
                selectedFriendsToAdd = emptySet()
            },
            title = { Text("Add Friends to Group", fontWeight = FontWeight.Bold) },
            text = {
                if (availableFriends.isEmpty()) {
                    Text("All your friends are already in this group!")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Select friends to add to \"${group.name}\":", style = MaterialTheme.typography.bodySmall)
                        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                            items(availableFriends) { friend ->
                                val isChecked = friend.uid in selectedFriendsToAdd
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedFriendsToAdd = if (isChecked) {
                                                selectedFriendsToAdd - friend.uid
                                            } else {
                                                selectedFriendsToAdd + friend.uid
                                            }
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            selectedFriendsToAdd = if (checked) {
                                                selectedFriendsToAdd + friend.uid
                                            } else {
                                                selectedFriendsToAdd - friend.uid
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(friend.avatarEmoji, fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(friend.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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
                                val res = FriendManager.addMembersToGroup(group.id, selectedFriendsToAdd.toList())
                                isAddingFriends = false
                                res.fold(
                                    onSuccess = {
                                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                                        showAddFriendsPicker = false
                                        selectedFriendsToAdd = emptySet()
                                        // Reload members
                                        members = FriendManager.fetchGroupMemberDetails(group.memberUids + selectedFriendsToAdd)
                                    },
                                    onFailure = {
                                        Toast.makeText(context, it.message ?: "Failed to add", Toast.LENGTH_SHORT).show()
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
                TextButton(onClick = { showAddFriendsPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = group.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Whatshot, contentDescription = null, tint = Color(0xFFFF5722), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("${group.streak}d Group Streak", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Group Code Share Banner
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "GROUP INVITE CODE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    group.groupCode.ifEmpty { "Generating..." },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )
                            }

                            FilledTonalButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Group Code", group.groupCode))
                                    Toast.makeText(context, "Copied: ${group.groupCode}", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy Code")
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Share this code with friends so they can join this group from their device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Members Header & Add Friends Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Members (${members.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    if (availableFriends.isNotEmpty()) {
                        TextButton(
                            onClick = { showAddFriendsPicker = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Friends", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // Members List
                if (isLoadingMembers) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(members) { member ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(member.avatarEmoji, fontSize = 22.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(member.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(member.friendCode, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Whatshot, contentDescription = null, tint = Color(0xFFFF5722), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("${member.currentStreak}d", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom actions: Leave Group button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showLeaveConfirm = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Leave Group")
                    }

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
}
