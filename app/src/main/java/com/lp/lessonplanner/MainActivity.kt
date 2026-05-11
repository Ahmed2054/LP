package com.lp.lessonplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.lp.lessonplanner.ui.screens.*
import com.lp.lessonplanner.viewmodel.LessonPlanViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LessonPlannerTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp() {
    val navController = rememberNavController()
    val viewModel: LessonPlanViewModel = viewModel()
    val updateAvailable by viewModel.updateAvailable.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    var currentTab by remember { mutableStateOf("Create") }

    // Redirect based on login state
    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) {
            currentTab = "Settings"
            navController.navigate("settings") {
                popUpTo(0)
            }
        } else if (navController.currentDestination?.route == "settings") {
            // Auto-navigate to create after login if we are on settings screen
            currentTab = "Create"
            navController.navigate("create") {
                popUpTo("settings") { inclusive = true }
            }
        }
    }

    updateAvailable?.let { update ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdate() },
            title = { Text("Update Available") },
            text = { 
                Column {
                    Text("A new version (${update.versionName}) is available.")
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text(update.releaseNotes, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.downloadUpdate(update) }) {
                    Text("Download & Install")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUpdate() }) {
                    Text("Later")
                }
            }
        )
    }

    Scaffold(
        bottomBar = {
            if (isLoggedIn) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentTab == "Create",
                        onClick = { currentTab = "Create"; navController.navigate("create") },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        label = { Text("Create") }
                    )
                    NavigationBarItem(
                        selected = currentTab == "History",
                        onClick = { currentTab = "History"; navController.navigate("history") },
                        icon = { Icon(Icons.Default.History, contentDescription = null) },
                        label = { Text("History") }
                    )
                    NavigationBarItem(
                        selected = currentTab == "Curriculum",
                        onClick = { currentTab = "Curriculum"; navController.navigate("curriculum") },
                        icon = { Icon(Icons.Default.LibraryBooks, contentDescription = null) },
                        label = { Text("Curriculum") }
                    )
                    NavigationBarItem(
                        selected = currentTab == "Settings",
                        onClick = { currentTab = "Settings"; navController.navigate("settings") },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Settings") }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (isLoggedIn) "create" else "settings",
            modifier = Modifier.padding(padding)
        ) {
            composable("create") { 
                if (isLoggedIn) CreateScreen(viewModel) 
            }
            composable("history") { 
                if (isLoggedIn) {
                    HistoryScreen(
                        viewModel = viewModel,
                        onPlanClick = { plan -> 
                            viewModel.selectHistoryPlan(plan)
                            navController.navigate("view_plan")
                        }
                    ) 
                }
            }
            composable("view_plan") {
                if (isLoggedIn) {
                    val plan by viewModel.selectedHistoryPlan.collectAsState()
                    plan?.let {
                        ViewPlanScreen(
                            plan = it,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onDelete = { 
                                viewModel.deletePlan(it)
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
            composable("curriculum") { 
                if (isLoggedIn) CurriculumScreen(viewModel) 
            }
            composable("settings") { SettingsScreen(viewModel) }
        }
    }
}

@Composable
fun LessonPlannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
