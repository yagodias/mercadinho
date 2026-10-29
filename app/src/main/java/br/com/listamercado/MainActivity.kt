package br.com.listamercado

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.listamercado.ui.AppViewModel
import br.com.listamercado.ui.MainScreen
import br.com.listamercado.ui.OnboardingScreen
import br.com.listamercado.ui.theme.ListaMercadoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ListaMercadoTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot(vm: AppViewModel = viewModel()) {
    val groupId by vm.groupId.collectAsStateWithLifecycle()
    if (groupId == null) {
        OnboardingScreen(vm)
    } else {
        MainScreen(vm)
    }
}
