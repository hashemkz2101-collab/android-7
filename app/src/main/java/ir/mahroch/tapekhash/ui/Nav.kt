package ir.mahroch.tapekhash.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ir.mahroch.tapekhash.data.Session
import ir.mahroch.tapekhash.ui.screens.AppSelectScreen
import ir.mahroch.tapekhash.ui.screens.LoginScreen
import ir.mahroch.tapekhash.ui.screens.accounting.AccountingMainScreen
import ir.mahroch.tapekhash.ui.screens.khash.KhashMainScreen
import ir.mahroch.tapekhash.ui.screens.sales.SalesMainScreen
import ir.mahroch.tapekhash.ui.screens.tape.TapeMainScreen
import ir.mahroch.tapekhash.ui.screens.tape2.Tape2MainScreen

object Routes {
    const val LOGIN = "login"
    const val APP_SELECT = "app_select"
    const val TAPE = "tape"
    const val TAPE2 = "tape2"
    const val KHASH = "khash"
    const val SALES = "sales"
    const val ACCOUNTING = "accounting"
}

@Composable
fun AppNavGraph() {
    val navController: NavHostController = rememberNavController()
    val startDestination = if (Session.isLoggedIn) Routes.APP_SELECT else Routes.LOGIN

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(onLoggedIn = {
                navController.navigate(Routes.APP_SELECT) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            })
        }
        composable(Routes.APP_SELECT) {
            AppSelectScreen(
                onOpenTape = { navController.navigate(Routes.TAPE) },
                onOpenTape2 = { navController.navigate(Routes.TAPE2) },
                onOpenKhash = { navController.navigate(Routes.KHASH) },
                onOpenSales = { navController.navigate(Routes.SALES) },
                onOpenAccounting = { navController.navigate(Routes.ACCOUNTING) },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.TAPE) {
            TapeMainScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TAPE2) {
            Tape2MainScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.KHASH) {
            KhashMainScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SALES) {
            SalesMainScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ACCOUNTING) {
            AccountingMainScreen(onBack = { navController.popBackStack() })
        }
    }
}
