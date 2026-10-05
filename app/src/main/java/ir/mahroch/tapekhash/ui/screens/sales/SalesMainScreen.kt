package ir.mahroch.tapekhash.ui.screens.sales

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

private enum class SalesTab { PRODUCTS, INVOICE, CUSTOMERS, INVOICES, REPORTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesMainScreen(onBack: () -> Unit) {
    var tab by remember { mutableStateOf(SalesTab.PRODUCTS) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("فروش کالا") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == SalesTab.PRODUCTS, onClick = { tab = SalesTab.PRODUCTS },
                    icon = { Icon(Icons.Default.Inventory2, contentDescription = null) }, label = { Text("کالاها") })
                NavigationBarItem(selected = tab == SalesTab.INVOICE, onClick = { tab = SalesTab.INVOICE },
                    icon = { Icon(Icons.Default.PointOfSale, contentDescription = null) }, label = { Text("فاکتور فروش") })
                NavigationBarItem(selected = tab == SalesTab.INVOICES, onClick = { tab = SalesTab.INVOICES },
                    icon = { Icon(Icons.Default.ReceiptLong, contentDescription = null) }, label = { Text("فاکتورها") })
                NavigationBarItem(selected = tab == SalesTab.CUSTOMERS, onClick = { tab = SalesTab.CUSTOMERS },
                    icon = { Icon(Icons.Default.Groups, contentDescription = null) }, label = { Text("مشتریان") })
                NavigationBarItem(selected = tab == SalesTab.REPORTS, onClick = { tab = SalesTab.REPORTS },
                    icon = { Icon(Icons.Default.Assessment, contentDescription = null) }, label = { Text("گزارش") })
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                SalesTab.PRODUCTS -> ProductsTab()
                SalesTab.INVOICE -> SaleInvoiceTab()
                SalesTab.CUSTOMERS -> CustomersTab()
                SalesTab.INVOICES -> SaleInvoicesTab()
                SalesTab.REPORTS -> SalesReportTab()
            }
        }
    }
}
