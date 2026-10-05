package ir.mahroch.tapekhash.ui.screens.khash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

private enum class KhashTab { EMPLOYEES, INVOICES, SMALLJOBS, PAYMENTS, ORDERS, REPORTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KhashMainScreen(onBack: () -> Unit) {
    var tab by remember { mutableStateOf(KhashTab.INVOICES) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("خرده کاری و فاکتور های خاش") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == KhashTab.INVOICES, onClick = { tab = KhashTab.INVOICES },
                    icon = { Icon(Icons.Default.ReceiptLong, contentDescription = null) }, label = { Text("فاکتور") })
                NavigationBarItem(selected = tab == KhashTab.SMALLJOBS, onClick = { tab = KhashTab.SMALLJOBS },
                    icon = { Icon(Icons.Default.Build, contentDescription = null) }, label = { Text("خرده‌کاری") })
                NavigationBarItem(selected = tab == KhashTab.PAYMENTS, onClick = { tab = KhashTab.PAYMENTS },
                    icon = { Icon(Icons.Default.Payments, contentDescription = null) }, label = { Text("پرداخت") })
                NavigationBarItem(selected = tab == KhashTab.ORDERS, onClick = { tab = KhashTab.ORDERS },
                    icon = { Icon(Icons.Default.LocalShipping, contentDescription = null) }, label = { Text("سفارشات") })
                NavigationBarItem(selected = tab == KhashTab.EMPLOYEES, onClick = { tab = KhashTab.EMPLOYEES },
                    icon = { Icon(Icons.Default.Groups, contentDescription = null) }, label = { Text("نیروها") })
                NavigationBarItem(selected = tab == KhashTab.REPORTS, onClick = { tab = KhashTab.REPORTS },
                    icon = { Icon(Icons.Default.Assessment, contentDescription = null) }, label = { Text("گزارش") })
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                KhashTab.EMPLOYEES -> EmployeesTab()
                KhashTab.INVOICES -> InvoicesTab()
                KhashTab.SMALLJOBS -> SmallJobsTab()
                KhashTab.PAYMENTS -> PaymentsTab()
                KhashTab.ORDERS -> OrdersTab()
                KhashTab.REPORTS -> ReportsTab()
            }
        }
    }
}
