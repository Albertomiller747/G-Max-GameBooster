package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.viewmodel.GameBoosterViewModel

@Composable
fun DeviceDiagnosticsScreen(
    viewModel: GameBoosterViewModel,
    modifier: Modifier = Modifier
) {
    ExtrasScreen(viewModel = viewModel, modifier = modifier)
}
