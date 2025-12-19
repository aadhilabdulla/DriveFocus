package com.example.drivefocus

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.drivefocus.services.DriveFocusService
import com.example.drivefocus.ui.theme.DriveFocusTheme
import com.example.drivefocus.viewmodel.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DriveFocusTheme {
                Scaffold() { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)){
                        HomeScreen()
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    // Collect the state from the ViewModel. This remains the same.
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val isDefaultCallerIdApp by viewModel.isDefaultCallerIdApp.collectAsState()
    val requestRoleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // After the user returns from the settings screen, re-check the status
        viewModel.checkDefaultCallerIdStatus()
    }
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            // Existing
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.FOREGROUND_SERVICE_LOCATION,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC,
            Manifest.permission.READ_CALL_LOG,
        )
    } else {
        arrayOf(
            // Existing
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CALL_LOG,

        )
    }

    val multiplePermissionResultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { perms ->
            val allGranted = perms.all { it.value }
            if (allGranted) {
                startService(context)
            }
            // Optional: Handle the case where permissions are denied
        }
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Here we use your ToggleButton and pass the state to it
        ToggleButton(
            selected = isServiceRunning,
            onUpdate = { selected ->
                if (selected) {
                    // This is the "Start" action
                    multiplePermissionResultLauncher.launch(permissionsToRequest)
                } else {
                    // This is the "Stop" action
                    stopService(context)
                }
            }
        )
        // This button only shows up if the app isn't the default and the service is not running.
        if (!isDefaultCallerIdApp) {
            Spacer(modifier = Modifier.height(24.dp))
            SetAsDefaultButton {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                    requestRoleLauncher.launch(intent)
                }
            }
        }

    }
}

@Composable
fun SetAsDefaultButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
    ) {
        Icon(imageVector = Icons.Default.Check, contentDescription = "Checkmark")
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "Set as Call Screening App")
    }
}
// Keep your original ToggleButton composable exactly as it was
@Composable
fun ToggleButton(
    selected: Boolean,
    onUpdate: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    contentPadding: PaddingValues = PaddingValues(16.dp),
) {
    // 1. Define your custom green color and the "unselected" color
    val selectedColor = Color(0xFF4CAF50)
    val unselectedColor = MaterialTheme.colorScheme.primary

    // 2. Animate the BORDER color between green and the primary theme color
    val animatedBorderColor by animateColorAsState(
        targetValue = if (selected) selectedColor else unselectedColor,
        label = "borderColor"
    )

    val size by animateDpAsState(targetValue = if (selected) 150.dp else 140.dp, label = "size")

    OutlinedButton(
        onClick = { onUpdate(!selected) },
        modifier = modifier.size(size),
        shape = shape,
        // 3. Apply the animated color to the border here
        border = BorderStroke(2.dp, animatedBorderColor),
        contentPadding = contentPadding,
        // 4. IMPORTANT: Ensure the container color is always transparent
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent
        )
    ) {
        Text(
            text = if (selected) "Stop" else "Start",
            fontSize = 24.sp
        )
    }
}



// startService and stopService functions remain unchanged
private fun startService(context: Context) {
    val intent = Intent(context, DriveFocusService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

private fun stopService(context: Context) {
    val intent = Intent(context, DriveFocusService::class.java)
    // Set the specific action to tell the service to shut down gracefully
    intent.action = DriveFocusService.ACTION_STOP_SERVICE
    // Use startService to deliver this command to the already-running service
    context.startService(intent)
}



@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    HomeScreen()
}