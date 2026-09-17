package id.xterm.xref.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.Activity
import id.xterm.xref.ui.home.DashboardScreen
import id.xterm.xref.ui.home.HomeViewModel
import id.xterm.xref.ui.navigation.Destination
import id.xterm.xref.ui.theme.NeonGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    homeViewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    var showInfoDialog by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(initialPage = BottomTab.entries.indexOf(BottomTab.Home)) {
        BottomTab.entries.size
    }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var splashElapsed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(homeViewModel.snackbarMessage) {
        homeViewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        if (!splashElapsed) {
            kotlinx.coroutines.delay(3000L)
            splashElapsed = true
        }
    }

    // Handle Back Press to maintain state and minimize app instead of closing
    BackHandler(enabled = splashElapsed) {
        when {
            // 1. If inside a specific room chat, go back to room list
            pagerState.currentPage == BottomTab.entries.indexOf(BottomTab.Room) && 
            homeViewModel.selectedRoomInRoomsTab != null -> {
                homeViewModel.selectedRoomInRoomsTab = null
            }
            // 2. If not on Home tab, go to Home tab
            pagerState.currentPage != BottomTab.entries.indexOf(BottomTab.Home) -> {
                coroutineScope.launch {
                    pagerState.animateScrollToPage(BottomTab.entries.indexOf(BottomTab.Home))
                }
            }
            // 3. If on Home tab, minimize app (move to back)
            else -> {
                (context as? Activity)?.moveTaskToBack(false)
            }
        }
    }

    if (!splashElapsed) {
        SplashScreen()
        return
    }

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
                            "Version: 1.05\n" +
                            "Creator: HEX\n\n" +
                            "Description: A professional broadcasting and starter tool for mig33 kick tournaments. Features include real-time match monitoring, tournament brackets, and automated 10vs10 match logic.",
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            val selectedIndex = pagerState.currentPage
            val isKeyboardVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp
            
            if (!isKeyboardVisible) {
                // Adjust height based on orientation for better visibility
                val barHeight = if (isLandscape) 56.dp else 80.dp
                val iconSize = if (isLandscape) 24.dp else 28.dp

                NavigationBar(
                    modifier = Modifier.height(barHeight),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    BottomTab.entries.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selectedIndex == index,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            icon = { 
                                Icon(
                                    imageVector = tab.icon, 
                                    contentDescription = tab.label,
                                    modifier = Modifier.size(iconSize)
                                ) 
                            },
                            label = null,
                            alwaysShowLabel = false,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NeonGreen,
                                selectedTextColor = NeonGreen,
                                unselectedIconColor = NeonGreen.copy(alpha = 0.5f),
                                unselectedTextColor = NeonGreen.copy(alpha = 0.5f),
                                indicatorColor = NeonGreen.copy(alpha = 0.1f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.ime)
                .fillMaxSize()
                .padding(4.dp)
                .border(1.dp, NeonGreen, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.background)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (BottomTab.entries[page]) {
                    BottomTab.Home -> DashboardScreen(
                        viewModel = homeViewModel,
                        onNavigateToRooms = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(BottomTab.entries.indexOf(BottomTab.Room))
                            }
                        }
                    )
                    BottomTab.Bracket -> BracketScreen(
                        homeViewModel = homeViewModel
                    )
                    BottomTab.Room -> RoomScreen(homeViewModel)
                }
            }
        }
    }
}

enum class BottomTab(val label: String, val icon: ImageVector, val destination: Destination) {
    Home("DASHBOARD", Icons.Rounded.Home, Destination.Home),
    Room("ROOM", Icons.AutoMirrored.Rounded.Chat, Destination.Room),
    Bracket("BRACKET", Icons.AutoMirrored.Rounded.List, Destination.Bracket)
}
