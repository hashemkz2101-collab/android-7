package ir.mahroch.tapekhash.ui.screens.accounting

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

private enum class AccountingTab { ENTRY, REPORT, PAYMENTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountingMainScreen(onBack: () -> Unit) {
    var tab by remember { mutableStateOf(AccountingTab.ENTRY) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("حساب چاپ های مستقیم کارگاه") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == AccountingTab.ENTRY, onClick = { tab = AccountingTab.ENTRY },
                    icon = { Icon(Icons.Default.Print, contentDescription = null) }, label = { Text("ثبت چاپ") })
                NavigationBarItem(selected = tab == AccountingTab.REPORT, onClick = { tab = AccountingTab.REPORT },
                    icon = { Icon(Icons.Default.Assessment, contentDescription = null) }, label = { Text("گزارش") })
                NavigationBarItem(selected = tab == AccountingTab.PAYMENTS, onClick = { tab = AccountingTab.PAYMENTS },
                    icon = { Icon(Icons.Default.Payments, contentDescription = null) }, label = { Text("پرداخت و مانده") })
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                AccountingTab.ENTRY -> EntryTab()
                AccountingTab.REPORT -> ReportTab()
                AccountingTab.PAYMENTS -> PaymentsBalanceTab()
            }
        }
    }
}
