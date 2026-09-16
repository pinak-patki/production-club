package com.Pinak.ProductiveClub.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.Pinak.ProductiveClub.util.FriendManager

@Composable
fun MiniSidebarRail(
    onOpenFriends: () -> Unit,
    onOpenGroups: () -> Unit,
    onQuickAdd: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTimetable: () -> Unit,
    modifier: Modifier = Modifier
) {
    val friendStatuses by FriendManager.friendStatuses.collectAsState()
    val groups by FriendManager.groups.collectAsState()

    Surface(
        modifier = modifier
            .width(58.dp)
            .wrapContentHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Section: Flame & Core Icons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Streak indicator
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF5722).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Whatshot,
                        contentDescription = "Streak",
                        tint = Color(0xFFFF5722),
                        modifier = Modifier.size(22.dp)
                    )
                }

                HorizontalDivider(
                    modifier = Modifier
                        .width(32.dp)
                        .padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Friends Icon with Badge (Person silhouette)
                RailIconButton(
                    icon = Icons.Default.Person,
                    contentDescription = "Friends",
                    badgeCount = friendStatuses.size,
                    onClick = onOpenFriends
                )

                // Groups Icon with Badge (Multiple people / cluster)
                RailIconButton(
                    icon = Icons.Default.Groups,
                    contentDescription = "Groups",
                    badgeCount = groups.size,
                    onClick = onOpenGroups
                )

                // Quick Add "+" button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { onQuickAdd() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add Friend or Group",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Timetable quick jump
                RailIconButton(
                    icon = Icons.Default.Schedule,
                    contentDescription = "Timetable",
                    badgeCount = 0,
                    onClick = onOpenTimetable
                )
            }

            // Bottom Section: Settings / Drawer expansion
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RailIconButton(
                    icon = Icons.Default.Tune,
                    contentDescription = "Settings & Privacy",
                    badgeCount = 0,
                    onClick = onOpenSettings
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RailIconButton(
    icon: ImageVector,
    contentDescription: String,
    badgeCount: Int,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(42.dp)
    ) {
        if (badgeCount > 0) {
            BadgedBox(
                badge = {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Text(badgeCount.toString())
                    }
                }
            ) {
                Icon(
                    icon,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        } else {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
