package com.example.veyrobynovadevs.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.veyrobynovadevs.ui.theme.VpnConnectedGlow
import com.example.veyrobynovadevs.ui.theme.VpnConnectedGreen
import com.example.veyrobynovadevs.ui.theme.VpnConnectingAmber
import com.example.veyrobynovadevs.ui.theme.VpnConnectingGlow
import com.example.veyrobynovadevs.ui.theme.VpnDisconnectedGlow
import com.example.veyrobynovadevs.ui.theme.VpnDisconnectedRed
import com.example.veyrobynovadevs.vpn.VpnState

@Composable
fun VpnPowerButton(
    vpnState: VpnState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (vpnState == VpnState.CONNECTING) 1.18f else if (vpnState == VpnState.CONNECTED) 1.08f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (vpnState == VpnState.CONNECTING) 700 else 1800,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val mainColor by animateColorAsState(
        targetValue = when (vpnState) {
            VpnState.CONNECTED -> VpnConnectedGreen
            VpnState.CONNECTING -> VpnConnectingAmber
            VpnState.DISCONNECTING -> VpnConnectingAmber
            VpnState.DISCONNECTED -> VpnDisconnectedRed
        },
        animationSpec = tween(500),
        label = "mainColor"
    )

    val glowColor by animateColorAsState(
        targetValue = when (vpnState) {
            VpnState.CONNECTED -> VpnConnectedGlow
            VpnState.CONNECTING -> VpnConnectingGlow
            VpnState.DISCONNECTING -> VpnConnectingGlow
            VpnState.DISCONNECTED -> VpnDisconnectedGlow
        },
        animationSpec = tween(500),
        label = "glowColor"
    )

    val statusText = when (vpnState) {
        VpnState.CONNECTED -> "CONNECTED"
        VpnState.CONNECTING -> "CONNECTING..."
        VpnState.DISCONNECTING -> "DISCONNECTING..."
        VpnState.DISCONNECTED -> "TAP TO CONNECT"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(200.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
        ) {
            // Outer Glowing Ring 2
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(glowColor.copy(alpha = 0.25f))
            )

            // Outer Glowing Ring 1
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(if (vpnState == VpnState.CONNECTING) pulseScale * 0.95f else 1.02f)
                    .clip(CircleShape)
                    .background(glowColor)
            )

            // Inner Button Circle
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(130.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                mainColor,
                                mainColor.copy(alpha = 0.85f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
                    .border(3.dp, mainColor, CircleShape)
            ) {
                Icon(
                    imageVector = when (vpnState) {
                        VpnState.CONNECTED -> Icons.Rounded.Shield
                        VpnState.CONNECTING, VpnState.DISCONNECTING -> Icons.Rounded.Sync
                        VpnState.DISCONNECTED -> Icons.Rounded.PowerSettingsNew
                    },
                    contentDescription = statusText,
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status pill
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(mainColor.copy(alpha = 0.15f))
                .border(1.dp, mainColor.copy(alpha = 0.4f), CircleShape)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = statusText,
                color = mainColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 1.sp
            )
        }
    }
}
