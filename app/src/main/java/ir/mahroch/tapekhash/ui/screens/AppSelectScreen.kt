package ir.mahroch.tapekhash.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.mahroch.tapekhash.data.Session

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectScreen(
    onOpenTape: () -> Unit,
    onOpenTape2: () -> Unit,
    onOpenKhash: () -> Unit,
    onOpenSales: () -> Unit,
    onOpenAccounting: () -> Unit,
    onLogout: () -> Unit
) {
    val user = Session.loadCachedUser()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("سلام، ${user?.displayName ?: ""}") },
                actions = {
                    IconButton(onClick = {
                        Session.clear()
                        onLogout()
                    }) { Icon(Icons.Default.ExitToApp, contentDescription = "خروج") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("کدام برنامه را می‌خواهید باز کنید؟", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(24.dp))

            if (user?.hasApp("tape") == true) {
                AppChoiceCard(
                    title = "مدیریت تپه‌های ستاره جنوب",
                    subtitle = "ثبت، جستجو، برداشت و برگشت تپه‌ها",
                    icon = Icons.Default.Inventory2,
                    onClick = onOpenTape
                )
                Spacer(Modifier.height(16.dp))
            }

            if (user?.hasApp("tape2") == true) {
                AppChoiceCard(
                    title = "مدیریت تپه‌های مروارید",
                    subtitle = "کپی برنامه‌ی تپه‌ها با باکس‌های جدید",
                    icon = Icons.Default.Inventory2,
                    onClick = onOpenTape2
                )
                Spacer(Modifier.height(16.dp))
            }

            if (user?.hasApp("khash") == true) {
                AppChoiceCard(
                    title = "خرده کاری و فاکتور های خاش",
                    subtitle = "فاکتورها، پرداخت‌ها، سفارشات و گزارش‌ها",
                    icon = Icons.Default.ReceiptLong,
                    onClick = onOpenKhash
                )
            }

            if (user?.hasApp("sales") == true) {
                Spacer(Modifier.height(16.dp))
                AppChoiceCard(
                    title = "فروش کالا",
                    subtitle = "کالاها، فاکتور فروش، مشتریان و گزارش فروش",
                    icon = Icons.Default.ShoppingCart,
                    onClick = onOpenSales
                )
            }

            if (user?.hasApp("accounting") == true) {
                Spacer(Modifier.height(16.dp))
                AppChoiceCard(
                    title = "حساب چاپ های مستقیم کارگاه",
                    subtitle = "ثبت چاپ مستقیم در کارگاه، گزارش، پرداخت و مانده‌حساب",
                    icon = Icons.Default.Calculate,
                    onClick = onOpenAccounting
                )
            }

            if (user != null && !user.hasApp("tape") && !user.hasApp("tape2") && !user.hasApp("khash") &&
                !user.hasApp("sales") && !user.hasApp("accounting")
            ) {
                Text("شما به هیچ برنامه‌ای دسترسی ندارید. با مدیر سیستم تماس بگیرید.")
            }
        }
    }
}

@Composable
private fun AppChoiceCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
