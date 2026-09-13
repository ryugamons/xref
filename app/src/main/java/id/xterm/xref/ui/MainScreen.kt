package id.xterm.xref.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Sports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import id.xterm.xref.core.match.MatchManager
import id.xterm.xref.data.repository.WebSocketRepository
import id.xterm.xref.ui.dashboard.DashboardScreen
import id.xterm.xref.ui.dashboard.DashboardViewModel
import id.xterm.xref.ui.home.HomeScreen
import id.xterm.xref.ui.home.HomeViewModel
import id.xterm.xref.ui.navigation.Destination
import id.xterm.xref.ui.theme.NeonGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    dashboardViewModel: DashboardViewModel = viewModel(),
    homeViewModel: HomeViewModel = viewModel()
) {
    val matchManager = dashboardViewModel.matchManager
    var showInfoDialog by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(initialPage = BottomTab.entries.indexOf(BottomTab.Home)) {
        BottomTab.entries.size
    }
    val coroutineScope = rememberCoroutineScope()

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = {
                Text(
                    text = "XREF Info",
                    color = NeonGreen,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "App Name: XREF\n" +
                            "Version: 1.0.0\n" +
                            "Creator: hex\n\n" +
                            "Description: A professional broadcasting and starter tool for mig33 kick tournaments. Features include real-time match monitoring, tournament brackets, and automated 10vs10 match logic with 3s timeout enforcement.",
                    color = NeonGreen
                )
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("CLOSE", color = NeonGreen)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            textContentColor = NeonGreen,
            titleContentColor = NeonGreen
        )
    }

    Scaffold(
        topBar = {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
                // Left spacing dummy
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(24.dp))
                
                Text(
                    text = "XREF",
                    color = NeonGreen,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                
                IconButton(
                    onClick = { showInfoDialog = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = "Info",
                        tint = NeonGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        bottomBar = {
            val selectedIndex = pagerState.currentPage
            NavigationBar {
                BottomTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedIndex == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(8.dp)
                .border(1.dp, NeonGreen, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.background)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (BottomTab.entries[page]) {
                    BottomTab.Home -> HomeScreen(homeViewModel)
                    BottomTab.Bracket -> BracketScreen(
                        homeViewModel = homeViewModel,
                        onLoadToDashboard = { teamA, teamB ->
                            matchManager.startMatch(teamA, teamB)
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(BottomTab.entries.indexOf(BottomTab.Dashboard))
                            }
                        }
                    )
                    BottomTab.Statistics -> StatisticsScreen(matchManager)
                    BottomTab.Room -> RoomScreen(matchManager)
                    BottomTab.Dashboard -> DashboardScreen(dashboardViewModel, homeViewModel)
                }
            }
        }
    }
}

@Composable
fun ScreenPlaceholder(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = title)
    }
}

enum class BottomTab(val label: String, val icon: ImageVector, val destination: Destination) {
    Home("Home", Icons.Rounded.Home, Destination.Home),
    Bracket("Bracket", Icons.AutoMirrored.Rounded.List, Destination.Bracket),
    Statistics("Stats", Icons.Rounded.Assessment, Destination.Statistics),
    Room("Room", Icons.AutoMirrored.Rounded.Chat, Destination.Room),
    Dashboard("Dash", Icons.Rounded.Sports, Destination.Dashboard)
}
