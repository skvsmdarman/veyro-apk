package com.example.veyrobynovadevs.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.veyrobynovadevs.model.V2RayServer
import com.example.veyrobynovadevs.ui.components.DeveloperWatermark
import com.example.veyrobynovadevs.ui.theme.CyanPrimary
import com.example.veyrobynovadevs.ui.theme.PingBadRed
import com.example.veyrobynovadevs.ui.theme.PingGoodGreen
import com.example.veyrobynovadevs.ui.theme.PingMediumOrange
import com.example.veyrobynovadevs.utils.CountryUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
    servers: List<V2RayServer>,
    selectedServer: V2RayServer?,
    isLoading: Boolean,
    pingProgress: Pair<Int, Int>?,
    onSelectServer: (V2RayServer) -> Unit,
    onAutoSelectBest: () -> Unit,
    onPasteClipboard: () -> Unit,
    onPingAll: () -> Unit,
    onRefreshServers: () -> Unit,
    onOpenImportDialog: () -> Unit,
    onDeleteServer: (String) -> Unit,
    onDeleteDeadServers: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedRegion by remember { mutableStateOf(CountryUtils.Region.ALL) }
    var selectedCountryCode by remember { mutableStateOf<String?>(null) }

    val availableCountries = remember(servers) {
        servers.map { CountryUtils.getCountryInfo(it) }
            .distinctBy { it.code }
            .sortedBy { it.name }
    }

    val filteredServers = remember(servers, selectedRegion, selectedCountryCode) {
        val matched = servers.filter { server ->
            val info = CountryUtils.getCountryInfo(server)
            val regionMatches = when (selectedRegion) {
                CountryUtils.Region.ALL -> true
                else -> info.region == selectedRegion
            }
            val countryMatches = if (selectedCountryCode == null) true else info.code == selectedCountryCode
            regionMatches && countryMatches
        }

        // Sort by lowest latency within filtered group (online servers first, sorted by latencyMs)
        matched.sortedWith(
            compareBy<V2RayServer> { !it.isOnline }
                .thenBy { if (it.isOnline && it.latencyMs > 0) it.latencyMs else Long.MAX_VALUE }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Server List",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Quick Paste from Clipboard
                    IconButton(
                        onClick = onPasteClipboard,
                        enabled = !isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentPaste,
                            contentDescription = "Paste Clipboard",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Ping Test Action
                    IconButton(
                        onClick = onPingAll,
                        enabled = !isLoading && pingProgress == null
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NetworkCheck,
                            contentDescription = "Ping All Servers",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Refresh Remote Action
                    IconButton(
                        onClick = onRefreshServers,
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh Remote Servers"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenImportDialog,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Import Server"
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Ping Testing Progress Bar
            AnimatedVisibility(visible = pingProgress != null) {
                if (pingProgress != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Testing server latencies...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${pingProgress.first}/${pingProgress.second}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = {
                                if (pingProgress.second > 0) pingProgress.first.toFloat() / pingProgress.second.toFloat()
                                else 0f
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Prominent "Test & Filter Live Proxies" Button
                item {
                    Button(
                        onClick = onPingAll,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        enabled = !isLoading && pingProgress == null,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NetworkCheck,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Test & Filter Live Proxies",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // Quick Action Buttons Row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val bestInFiltered = filteredServers.firstOrNull { it.isOnline }
                                if (bestInFiltered != null) {
                                    onSelectServer(bestInFiltered)
                                } else {
                                    onAutoSelectBest()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Auto-Best",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Button(
                            onClick = onPasteClipboard,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentPaste,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Paste Clipboard",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // Delete Dead Servers Button (when dead servers exist)
                val deadCount = servers.count { !it.isOnline && it.lastTestedAt > 0L }
                if (deadCount > 0) {
                    item {
                        Button(
                            onClick = onDeleteDeadServers,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Delete All Dead Servers ($deadCount)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // Region Filter Chips Row
                item {
                    Column {
                        Text(
                            text = "REGION FILTER",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(CountryUtils.Region.entries.toTypedArray()) { region ->
                                val isRegionSelected = (selectedRegion == region && selectedCountryCode == null)
                                FilterChip(
                                    selected = isRegionSelected,
                                    onClick = {
                                        selectedRegion = region
                                        selectedCountryCode = null
                                    },
                                    label = {
                                        Text(
                                            text = region.displayName,
                                            fontWeight = if (isRegionSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }
                }

                // Country Filter Chips Row
                if (availableCountries.isNotEmpty()) {
                    item {
                        Column {
                            Text(
                                text = "COUNTRY FILTER",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    FilterChip(
                                        selected = selectedCountryCode == null,
                                        onClick = { selectedCountryCode = null },
                                        label = {
                                            Text(
                                                text = "All Countries 🌐",
                                                fontWeight = if (selectedCountryCode == null) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 12.sp
                                            )
                                        }
                                    )
                                }

                                items(availableCountries) { country ->
                                    val isCountrySelected = (selectedCountryCode == country.code)
                                    FilterChip(
                                        selected = isCountrySelected,
                                        onClick = {
                                            selectedCountryCode = if (isCountrySelected) null else country.code
                                        },
                                        label = {
                                            Text(
                                                text = "${country.flagEmoji} ${country.name}",
                                                fontWeight = if (isCountrySelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 12.sp
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Header title & count
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AVAILABLE SERVERS (${filteredServers.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Servers list items (sorted by lowest latency within filtered selection)
                items(filteredServers, key = { it.id }) { server ->
                    val isSelected = (selectedServer?.id == server.id) || (selectedServer == null && server == filteredServers.firstOrNull())
                    val isTopBest = filteredServers.firstOrNull { it.isOnline }?.id == server.id

                    ServerListItem(
                        server = server,
                        isSelected = isSelected,
                        isTopBest = isTopBest,
                        onSelect = { onSelectServer(server) },
                        onDelete = if (server.isCustom) {
                            { onDeleteServer(server.id) }
                        } else null
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    DeveloperWatermark()
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }
}

@Composable
private fun ServerListItem(
    server: V2RayServer,
    isSelected: Boolean,
    isTopBest: Boolean,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val pingColor = when {
        server.lastTestedAt == 0L -> MaterialTheme.colorScheme.onSurfaceVariant
        !server.isOnline -> PingBadRed
        server.latencyMs in 0..150 -> PingGoodGreen
        server.latencyMs in 151..350 -> PingMediumOrange
        else -> PingGoodGreen
    }

    val countryInfo = CountryUtils.getCountryInfo(server)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onSelect() }
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                } else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = onSelect
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = countryInfo.flagEmoji,
                    fontSize = 20.sp
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = server.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        // Highlight top server with "BEST" badge
                        if (isTopBest && server.isOnline) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(PingGoodGreen.copy(alpha = 0.2f))
                                    .border(1.dp, PingGoodGreen, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Star,
                                        contentDescription = "BEST",
                                        tint = PingGoodGreen,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "LOWEST PING ⚡",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = PingGoodGreen
                                    )
                                }
                            }
                        }

                        if (server.protocol != "vless") {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "UNSUPPORTED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        } else if (server.isCustom) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(CyanPrimary.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "CUSTOM",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyanPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${countryInfo.name} • ${server.protocol.uppercase()} • ${server.address}:${server.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Ping Badge & Delete Action
            Row(verticalAlignment = Alignment.CenterVertically) {
                val pingText = when {
                    server.lastTestedAt == 0L -> "UNTESTED"
                    server.isOnline -> "${server.latencyMs} ms"
                    else -> "DEAD / UNREACHABLE"
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(pingColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = pingText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = pingColor
                    )
                }

                if (onDelete != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete Server",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
