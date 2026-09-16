package com.Pinak.ProductiveClub.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun TermsAndConditionsDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Gavel,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                "Terms & Privacy",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Productive Club Guidelines",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TermsSection(
                        icon = Icons.Default.Handshake,
                        title = "1. Mutual Accountability Purpose",
                        body = "Productive Club is designed to foster discipline, focus, and peer accountability. By using this service, you agree to participate with honest intentions, supporting your friends and group members in maintaining daily productive habits."
                    )

                    TermsSection(
                        icon = Icons.Default.PhotoCamera,
                        title = "2. Proof Uploads & Media Storage",
                        body = "Daily proof photos and written reflections uploaded through the app are stored securely on cloud infrastructure (Cloudinary & Google Firestore). Proofs are shared exclusively with your confirmed mutual friends and study groups for verification. You retain the right to delete your daily summary photos at any time."
                    )

                    TermsSection(
                        icon = Icons.Default.PieChart,
                        title = "3. Screen Time & Data Privacy",
                        body = "Screen time is queried directly from Android's UsageStatsManager on your device. We NEVER read or collect private messaging contents, browsing history, passwords, or keystrokes. Only high-level foreground durations and top app categories are displayed. You can disable screen time sharing at any time in Settings."
                    )

                    TermsSection(
                        icon = Icons.Default.Security,
                        title = "4. Device Permissions Usage",
                        body = "• Camera: Strictly used to capture proof photos when you choose to upload.\n• Notifications: Used to alert you of scheduled timetable alarms and the 9:00 PM summary prompt.\n• Exact Alarms: Used to wake the device precisely at your chosen schedule times.\n• Usage Access: Used exclusively to compute your daily digital wellbeing breakdown."
                    )

                    TermsSection(
                        icon = Icons.Default.Groups,
                        title = "5. Community Conduct & Group Rules",
                        body = "Harassment, inappropriate imagery, or malicious behavior within study groups is strictly prohibited. Group owners have the authority to manage memberships, transfer leadership, or disband groups. You may unfriend any user or leave any group whenever you wish, which instantly breaks all mutual cloud ties."
                    )

                    TermsSection(
                        icon = Icons.Default.DeleteOutline,
                        title = "6. Data Control & Account Rights",
                        body = "You maintain complete ownership of your routine and timetable. You can edit tasks, clear historical summaries, or unlink friendships directly from the interface with immediate cloud synchronization."
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("I Understand & Agree")
                }
            }
        }
    }
}

@Composable
private fun TermsSection(
    icon: ImageVector,
    title: String,
    body: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
