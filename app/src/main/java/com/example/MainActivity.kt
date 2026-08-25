package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.example.network.RewardsApiService
import androidx.lifecycle.viewModelScope
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException

data class UnifiedCorporateWallet(
    val tokenBalance: Double = 5000.0,
    val exchangeRate: Int = 100,
    val zenditBalanceCents: Long = 48500,
    val giftbitBalanceCents: Long = 250000,
    val currency: String = "USD",
    val caftBalance: Double = 5.0,
    val ctcBalance: Double = 10.0,
    val jmgrBalance: Double = 100.0,
    val ethPrice: Double = 3200.00,
    val polPrice: Double = 0.45
)

data class WalletTransactionRecord(
    val id: String,
    val timestamp: Long,
    val action: String,
    val tokenAmount: Double,
    val fiatAmountCents: Long,
    val status: String = "SUCCESS"
)

class RewardsViewModel : ViewModel() {
    private val _walletState = MutableStateFlow(UnifiedCorporateWallet())
    val walletState: StateFlow<UnifiedCorporateWallet> = _walletState

    private val _history = MutableStateFlow<List<WalletTransactionRecord>>(emptyList())
    val history: StateFlow<List<WalletTransactionRecord>> = _history

    private val _jmgrVotesApprove = MutableStateFlow(0)
    val jmgrVotesApprove: StateFlow<Int> = _jmgrVotesApprove

    private val _jmgrProposalExecuted = MutableStateFlow(false)
    val jmgrProposalExecuted: StateFlow<Boolean> = _jmgrProposalExecuted

    private val apiService = com.example.network.NetworkClient.apiService

    init {
        setupRemoteConfig()
    }

    private fun setupRemoteConfig() {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        remoteConfig.addOnConfigUpdateListener(object : ConfigUpdateListener {
            override fun onUpdate(configUpdate: ConfigUpdate) {
                android.util.Log.d("RemoteConfig", "Updated keys: " + configUpdate.updatedKeys)
                remoteConfig.activate().addOnCompleteListener {
                    if (it.isSuccessful) {
                        android.util.Log.d("RemoteConfig", "Activated updated config")
                        // Refresh data or update UI as necessary
                    }
                }
            }

            override fun onError(error: FirebaseRemoteConfigException) {
                android.util.Log.e("RemoteConfig", "Config update error", error)
            }
        })
    }

    fun refreshBalances() {
        viewModelScope.launch {
            try {
                val response = apiService.getWalletStatus()
                if (response.status == "SUCCESS" && response.wallet != null) {
                    _walletState.update { 
                        it.copy(
                            tokenBalance = response.wallet.tokenBalance,
                            caftBalance = response.wallet.caftBalance,
                            ctcBalance = response.wallet.ctcBalance,
                            jmgrBalance = response.wallet.jmgrBalance,
                            zenditBalanceCents = response.wallet.zenditBalanceCents.toLong(),
                            giftbitBalanceCents = response.wallet.giftbitBalanceCents.toLong()
                            // Note: Oracle pricing could also be updated here
                        ) 
                    }
                }
            } catch (e: Exception) {
                // Fallback to local state if network is unavailable during simulation
                e.printStackTrace()
            }
        }
    }

    fun swapAsset(tokenType: String, amount: Double) {
        viewModelScope.launch {
            try {
                val response = apiService.executeOracleSwap(
                    com.example.network.SwapRequest(
                        tokenType = tokenType,
                        amount = amount,
                        idempotencyKey = "SWAP-${System.currentTimeMillis()}"
                    )
                )
                if (response.status == "SUCCESS") {
                    refreshBalances()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun voteJmgr(approve: Boolean, amount: Double = 10.0) {
        viewModelScope.launch {
            try {
                val response = apiService.voteJmgr(
                    com.example.network.VoteRequest(
                        proposalId = "PROP-901",
                        approve = approve
                    )
                )
                if (response.status == "SUCCESS") {
                    refreshBalances()
                    _jmgrProposalExecuted.value = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun allocateFunds(targetProvider: String, amount: Double) {
        viewModelScope.launch {
            try {
                val response = apiService.allocateFunds(
                    com.example.network.AllocateRequest(
                        targetProvider = targetProvider,
                        amount = amount
                    )
                )
                if (response.status == "SUCCESS") {
                    refreshBalances()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = "Transactions"
        val descriptionText = "Transaction and Governance Notifications"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel("TX_CHANNEL", name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

fun showNotification(context: Context, title: String, message: String) {
    val builder = NotificationCompat.Builder(context, "TX_CHANNEL")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)

    with(NotificationManagerCompat.from(context)) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }
}

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        com.example.network.SessionManager.init(this)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        createNotificationChannel(this)
        
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: RewardsViewModel = viewModel()
                RewardsGatewayScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsGatewayScreen(viewModel: RewardsViewModel) {
    val walletState by viewModel.walletState.collectAsState()
    val history by viewModel.history.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshBalances()
    }

    var mintAmount by remember { mutableStateOf("1000") }
    var allocateAmount by remember { mutableStateOf("500") }
    var allocateTarget by remember { mutableStateOf("zendit") }
    var fundToken by remember { mutableStateOf("USDC") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rewards Gateway", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                WalletBalancesSection(walletState)
            }

            item {
                OraclePriceFeedSection(walletState)
            }

            item {
                AssetManagerSection(viewModel = viewModel, walletState = walletState)
            }

            item {
                AllocationSection(
                    allocateAmount = allocateAmount,
                    onAllocateAmountChange = { allocateAmount = it },
                    allocateTarget = allocateTarget,
                    onTargetChange = { allocateTarget = it },
                    onAllocate = {
                        val amount = allocateAmount.toDoubleOrNull() ?: 0.0
                        viewModel.allocateFunds(allocateTarget, amount)
                        allocateAmount = ""
                    },
                    maxTokens = walletState.tokenBalance
                )
            }

            item {
                Text(
                    text = "Transaction History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            if (history.isEmpty()) {
                item {
                    Text("No transactions yet.", color = Color.Gray, modifier = Modifier.padding(bottom = 16.dp))
                }
            } else {
                items(history) { record ->
                    TransactionRecordItem(record)
                }
            }
        }
    }
}

@Composable
fun WalletBalancesSection(walletState: UnifiedCorporateWallet) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
    ) {
        Box(modifier = Modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(
            colors = listOf(Color(0xFF06B6D4), Color(0xFF3B82F6))
        ))) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "UNIFIED WEB3 CREDIT VALUE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        String.format(Locale.US, "%,.0f", walletState.tokenBalance),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "TOKENS",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("📡 Zendit: ${formatFiat(walletState.zenditBalanceCents, walletState.currency)}", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("🎁 Giftbit: ${formatFiat(walletState.giftbitBalanceCents, walletState.currency)}", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    
    Spacer(modifier = Modifier.height(16.dp))
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Institutional Reserve Holdings", fontSize = 14.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("🏢 CAFT: ${walletState.caftBalance}", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                Text("💳 CTC: ${walletState.ctcBalance}", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                Text("📈 JMGR: ${walletState.jmgrBalance}", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun OraclePriceFeedSection(walletState: UnifiedCorporateWallet) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔮 Chainlink Oracle Feeds", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Text("LIVE", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ETH/USD", fontSize = 12.sp, color = Color.Gray)
                    Text("$${String.format(Locale.US, "%,.2f", walletState.ethPrice)}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("POL/USD", fontSize = 12.sp, color = Color.Gray)
                    Text("$${String.format(Locale.US, "%,.2f", walletState.polPrice)}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun BalanceItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetManagerSection(viewModel: RewardsViewModel, walletState: UnifiedCorporateWallet) {
    var activeTab by remember { mutableStateOf("swap") }
    val jmgrVotes by viewModel.jmgrVotesApprove.collectAsState()
    val jmgrExecuted by viewModel.jmgrProposalExecuted.collectAsState()
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { activeTab = "swap" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeTab == "swap") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Facility Swaps")
                }
                Button(
                    onClick = { activeTab = "governance" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeTab == "governance") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("JMGR Consensus")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (activeTab == "swap") {
                Text("Liquidity Optimization Engines", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("CAFT Balance: ${walletState.caftBalance}", fontSize = 12.sp, color = Color.Gray)
                Button(
                    onClick = { 
                        viewModel.swapAsset("CAFT", 1.0) 
                        showNotification(context, "Swap Executed", "1 CAFT liquidated for ${String.format(Locale.US, "%.4f", 250000.0 / walletState.ethPrice)} ETH.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Liquefy 1 CAFT (${String.format(Locale.US, "%.4f", 250000.0 / walletState.ethPrice)} ETH)")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("CTC Balance: ${walletState.ctcBalance}", fontSize = 12.sp, color = Color.Gray)
                Button(
                    onClick = { 
                        viewModel.swapAsset("CTC", 1.0) 
                        showNotification(context, "Swap Executed", "1 CTC liquidated for ${String.format(Locale.US, "%.4f", 25000.0 / walletState.ethPrice)} ETH.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                ) {
                    Text("Liquefy 1 CTC (${String.format(Locale.US, "%.4f", 25000.0 / walletState.ethPrice)} ETH)")
                }
            } else {
                Text("JMGR Corporate Governance Monitor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Consensus Gateway Threshold: 66.6% (2 of 3 Fiduciary Seats Required)", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 16.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = androidx.compose.foundation.BorderStroke(2.dp, if (jmgrExecuted) Color(0xFF4CAF50) else Color(0xFFFFC107))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Proposal: PROP-901", fontWeight = FontWeight.Bold)
                            Text(
                                if (jmgrExecuted) "EXECUTED (POL SWAPPED)" else "PENDING CONSENSUS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (jmgrExecuted) Color(0xFF4CAF50) else Color(0xFFFFC107),
                                modifier = Modifier
                                    .background(if (jmgrExecuted) Color(0xFF1B5E20).copy(alpha = 0.2f) else Color(0xFFFFC107).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Requested Burn: 100,000 JMGR Shares", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Consensus Progress", fontSize = 12.sp, color = Color.Gray)
                            val pct = (jmgrVotes / 3f) * 100
                            Text("$jmgrVotes / 3 Approved (${String.format(Locale.US, "%.1f", pct)}%)", fontSize = 12.sp, color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { jmgrVotes / 3f },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = if (jmgrExecuted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        )
                        
                        if (!jmgrExecuted) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { 
                                        viewModel.voteJmgr(true, 100000.0) 
                                        showNotification(context, "Governance Vote Logged", "You approved Proposal PROP-901.")
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("\uD83D\uDC4D Approve Swap", fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { viewModel.voteJmgr(false, 0.0) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Abstain", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MintingSection(
    mintAmount: String,
    onMintAmountChange: (String) -> Unit,
    onMint: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AddCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mint Tokens", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = mintAmount,
                    onValueChange = onMintAmountChange,
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Button(onClick = onMint) {
                    Text("Mint")
                }
            }
        }
    }
}

@Composable
fun AllocationSection(
    allocateAmount: String,
    onAllocateAmountChange: (String) -> Unit,
    allocateTarget: String,
    onTargetChange: (String) -> Unit,
    onAllocate: () -> Unit,
    maxTokens: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Allocate Funds", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = allocateTarget == "zendit",
                    onClick = { onTargetChange("zendit") },
                    label = { Text("Zendit") }
                )
                FilterChip(
                    selected = allocateTarget == "giftbit",
                    onClick = { onTargetChange("giftbit") },
                    label = { Text("Giftbit") }
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = allocateAmount,
                    onValueChange = onAllocateAmountChange,
                    label = { Text("Token Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = onAllocate,
                    enabled = (allocateAmount.toDoubleOrNull() ?: 0.0) in 0.001..maxTokens
                ) {
                    Text("Allocate")
                }
            }
        }
    }
}

@Composable
fun TransactionRecordItem(record: WalletTransactionRecord) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = if (record.action == "TOKEN_MINT") Icons.Default.ArrowDownward else Icons.AutoMirrored.Filled.List
            val color = if (record.action == "TOKEN_MINT") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color)
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(text = record.action.replace("_", " "), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                Text(text = sdf.format(Date(record.timestamp)), fontSize = 12.sp, color = Color.Gray)
            }
            
            Column(horizontalAlignment = Alignment.End) {
                if (record.action == "TOKEN_MINT") {
                    Text("+${String.format(Locale.US, "%,.0f", record.tokenAmount)} TKN", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("-${String.format(Locale.US, "%,.0f", record.tokenAmount)} TKN", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Text("+${formatFiat(record.fiatAmountCents, "USD")}", fontSize = 12.sp, color = Color(0xFF4CAF50))
                }
            }
        }
    }
}

fun formatFiat(cents: Long, currency: String): String {
    val dollars = cents / 100.0
    return String.format(Locale.US, "$%,.2f", dollars)
}
