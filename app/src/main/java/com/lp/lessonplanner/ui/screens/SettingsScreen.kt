package com.lp.lessonplanner.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lp.lessonplanner.viewmodel.CreditPackage
import com.lp.lessonplanner.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: LessonPlanViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val model by viewModel.model.collectAsState()
    val userCredits by viewModel.userCredits.collectAsState()
    val creditPlans by viewModel.creditPlans.collectAsState()
    val isFetchingPlans by viewModel.isFetchingPlans.collectAsState()
    val showManualPaymentDialog by viewModel.showManualPaymentDialog.collectAsState()
    
    val cloudUserId by viewModel.cloudUserId.collectAsState()
    val phoneNumber by viewModel.phoneNumber.collectAsState()
    val pin by viewModel.pin.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }
    var tempModel by remember(model) { mutableStateOf(model) }
    var tempPhone by remember(phoneNumber) { mutableStateOf(phoneNumber) }
    var tempPin by remember(pin) { mutableStateOf(pin) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showRedeemDialog by remember { mutableStateOf(false) }
    var redemptionCode by remember { mutableStateOf("") }
    var showRedemptionHistoryDialog by remember { mutableStateOf(false) }

    val redemptionHistory by viewModel.redemptionHistory.collectAsState()
    val totalRedemptions by viewModel.totalRedemptions.collectAsState()
    val currentRedemptionPage by viewModel.currentRedemptionPage.collectAsState()

    LaunchedEffect(showRedemptionHistoryDialog) {
        if (showRedemptionHistoryDialog) {
            viewModel.loadRedemptionHistory(0)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearErrorMessage()
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Factory Reset") },
            text = { Text("This will delete all subjects, curriculum data, and saved lesson plans. Are you sure?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.factoryReset()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("Reset Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showManualPaymentDialog != null) {
        val pkg = showManualPaymentDialog!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissManualPayment() },
            title = { Text("How to Purchase & Redeem") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Follow these steps to get your credits:", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    
                    Text("1. Payment", fontWeight = FontWeight.SemiBold)
                    Text("Send GHS ${pkg.priceString} via MoMo to:")
                    Surface(
                        color = Color(0xFFF1F8E9),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Text(
                            "0553484762",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                    Text("Account Name: Ahmed Ofosu", fontSize = 12.sp)
                    
                    Spacer(Modifier.height(12.dp))
                    Text("2. Verification", fontWeight = FontWeight.SemiBold)
                    Text("Take a screenshot of your payment receipt.")
                    
                    Spacer(Modifier.height(12.dp))
                    Text("3. Get Code", fontWeight = FontWeight.SemiBold)
                    Text("Click the button below to send the screenshot to us on WhatsApp. We will send you a unique Redemption Code.")
                    
                    Spacer(Modifier.height(12.dp))
                    Text("4. Redeem", fontWeight = FontWeight.SemiBold)
                    Text("Once you have the code, come back settings, click 'Redeem Purchased Code' button and enter it to instantly add credits to your account.")
                }
            },
            confirmButton = {
                Button(onClick = { 
                    viewModel.openWhatsApp(pkg)
                }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Send to WhatsApp")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissManualPayment() }) {
                    Text("Close")
                }
            }
        )
    }

    if (showRedeemDialog) {
        AlertDialog(
            onDismissRequest = { if (!uiState.isLoading) showRedeemDialog = false },
            title = { Text("Redeem Code") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Enter the redemption code sent to you:", modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = redemptionCode,
                        onValueChange = { code: String -> redemptionCode = code },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("LP5-XXXX") },
                        singleLine = true,
                        enabled = !uiState.isLoading
                    )
                    if (uiState.isLoading) {
                        Spacer(Modifier.height(16.dp))
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Text("Verifying code...", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = redemptionCode.isNotBlank() && !uiState.isLoading,
                    onClick = {
                        viewModel.redeemCode(redemptionCode)
                    }
                ) {
                    Text("Redeem")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !uiState.isLoading,
                    onClick = { showRedeemDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Auto-close dialog on success
    LaunchedEffect(userCredits) {
        if (showRedeemDialog) {
            showRedeemDialog = false
            redemptionCode = ""
        }
    }

    if (showRedemptionHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showRedemptionHistoryDialog = false },
            title = { Text("Redemption History") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    if (redemptionHistory.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No redemptions found", color = Color.Gray)
                        }
                    } else {
                        Column {
                            redemptionHistory.forEach { redemption ->
                                val dateStr = remember(redemption.date) {
                                    val sdf = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
                                    sdf.format(java.util.Date(redemption.date))
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(redemption.code, fontWeight = FontWeight.Bold)
                                        Text("+${redemption.amount} Credits", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                    }
                                    Text(dateStr, fontSize = 12.sp, color = Color.Gray)
                                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                                }
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val totalPages = (totalRedemptions + 4) / 5
                                Text("Page ${currentRedemptionPage + 1} of $totalPages", fontSize = 12.sp)
                                
                                Row {
                                    IconButton(
                                        enabled = currentRedemptionPage > 0,
                                        onClick = { viewModel.loadRedemptionHistory(currentRedemptionPage - 1) }
                                    ) {
                                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous")
                                    }
                                    IconButton(
                                        enabled = (currentRedemptionPage + 1) * 5 < totalRedemptions,
                                        onClick = { viewModel.loadRedemptionHistory(currentRedemptionPage + 1) }
                                    ) {
                                        Icon(Icons.Default.ChevronRight, contentDescription = "Next")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRedemptionHistoryDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // User Account Section
            Text(
                "Account",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (isLoggedIn) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Phone: $phoneNumber", fontWeight = FontWeight.Bold)
                                Text("User ID: ${cloudUserId.take(8)}...", fontSize = 12.sp)
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { viewModel.logout() }) {
                                Text("Logout", color = Color.Red)
                            }
                        }
                    } else {
                        Column {
                            Text("Login to sync across devices.", fontSize = 12.sp)
                            Spacer(Modifier.height(12.dp))
                            
                            OutlinedTextField(
                                value = tempPhone,
                                onValueChange = { phone: String -> 
                                    if (phone.length <= 10) {
                                        tempPhone = phone 
                                    }
                                },
                                label = { Text("Phone Number") },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("e.g. 0553484762") },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                                )
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = tempPin,
                                onValueChange = { pin: String -> if (pin.length <= 4) tempPin = pin },
                                label = { Text("4-Digit PIN") },
                                modifier = Modifier.fillMaxWidth(),
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                                )
                            )
                            
                            Spacer(Modifier.height(16.dp))
                            
                            Button(
                                onClick = { viewModel.loginOrRegister(tempPhone, tempPin) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = tempPhone.length >= 10 && tempPin.length == 4 && !uiState.isLoading
                            ) {
                                if (uiState.isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                                } else {
                                    Text("Login / Register")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Credits & Payment Section
            if (isLoggedIn) {
                Text(
                    "Credits & Support",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Current Balance")
                                Text(
                                    "$userCredits Credits",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { viewModel.fetchUserCredits() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh Credits")
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = { showRedeemDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Icon(Icons.Default.Redeem, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Redeem Purchased Code")
                        }

                        TextButton(
                            onClick = { showRedemptionHistoryDialog = true },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("View Redemption History")
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    "Top Up Credits",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                if (isFetchingPlans) {
                    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    creditPlans.forEach { plan ->
                        CreditPlanItem(plan) { viewModel.showManualPayment(plan) }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }

            // AI Settings Section
            if (isLoggedIn) {
                Text(
                    "AI Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                var expanded by remember { mutableStateOf(false) }
                val modelOptions = listOf("low", "high")
                val currentDisplayModel = if (tempModel == "deepseek-reasoner") "high" else "low"

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = currentDisplayModel,
                        onValueChange = { _: String -> },
                        readOnly = true,
                        label = { Text("AI Model (Intelligence Level)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        modelOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    tempModel = if (option == "high") "deepseek-reasoner" else "deepseek-chat"
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.updateAiModel(tempModel) },
                    modifier = Modifier.align(Alignment.End),
                    enabled = tempModel != model
                ) {
                    Text("Save AI Settings")
                }

                Spacer(Modifier.height(32.dp))

                // Dangerous Zone
                Text(
                    "Danger Zone",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.Red,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Factory Reset App")
                }

                Spacer(Modifier.height(16.dp))
            }

            // About & Disclaimer Section
            Text(
                "About & Disclaimer",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "About This App",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "Lesson Planner is an AI-powered tool specifically designed for Ghanaian teachers to generate high-quality lesson plans, notes, and assessment questions based on the Ghanaian curriculum for basic schools. Our goal is to empower educators by reducing administrative workload.",
                        fontSize = 13.sp,
                        textAlign = TextAlign.Justify,
                        lineHeight = 18.sp
                    )

                    Spacer(Modifier.height(16.dp))

                    Text(
                        "Disclaimer",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.Red.copy(alpha = 0.8f)
                    )
                    Text(
                        "The content generated by this AI is for guidance only. Teachers should review and adapt all plans to meet specific classroom needs and curriculum standards. The developer is not responsible for any inaccuracies in generated content.",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Justify,
                        lineHeight = 16.sp
                    )

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )
                    Spacer(Modifier.height(16.dp))

                    Text(
                        "Developer Info",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text("Mr. Ahmed Ofosu", fontSize = 14.sp)
                    
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.openDialer() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = "Call", tint = MaterialTheme.colorScheme.primary)
                        }
                        Text("Call", fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))

                        Spacer(Modifier.width(24.dp))

                        IconButton(
                            onClick = { viewModel.openWhatsAppDirect("") },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "WhatsApp", tint = Color(0xFF2E7D32))
                        }
                        Text("WhatsApp", fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                    }

                    Spacer(Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = { viewModel.checkForUpdates(isSilent = false) },
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Check for Updates")
                    }
                }
            }
            
            Spacer(Modifier.height(48.dp))
            
            Text(
                "Lesson Planner v1.0.5\nDeveloped for Teachers",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun CreditPlanItem(plan: CreditPackage, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(plan.name, fontWeight = FontWeight.Bold)
                Text("${plan.credits} Credits", color = MaterialTheme.colorScheme.primary)
            }
            Text(
                "GHS ${plan.priceString}",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp
            )
        }
    }
}
