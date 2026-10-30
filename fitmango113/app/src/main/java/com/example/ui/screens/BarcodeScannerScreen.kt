package com.example.ui.screens

import com.example.ui.components.FitMangoIcon as Icon

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.components.*
import com.example.ui.theme.*
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
fun BarcodeScannerScreen(
    onBarcodeScanned: (String) -> Unit,
    onBack: () -> Unit,
    onReadLabel: (() -> Unit)? = null,
    onRecognizePhoto: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasEmittedBarcode by remember { mutableStateOf(false) }
    var manualBarcodeInput by remember { mutableStateOf("") }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    var boundProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var activeScanner by remember { mutableStateOf<com.google.mlkit.vision.barcode.BarcodeScanner?>(null) }
    var activeAnalysis by remember { mutableStateOf<ImageAnalysis?>(null) }
    var disposed by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            disposed = true
            activeAnalysis?.clearAnalyzer()
            boundProvider?.unbindAll()
            activeScanner?.close()
            analyzerExecutor.shutdown()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Skaner produktów", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("barcode_scanner_back_button")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Wstecz", tint = MangoYellow)
                    }
                },
                actions = { onRecognizePhoto?.let { action -> TextButton(onClick = action) { Text("Zdjęcie") } }; onReadLabel?.let { action -> TextButton(onClick = action) { Text("Etykieta") } } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = Cream
                )
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            if (disposed) return@addListener
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                boundProvider = cameraProvider
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                val options = BarcodeScannerOptions.Builder()
                                    .setBarcodeFormats(
                                        Barcode.FORMAT_EAN_13,
                                        Barcode.FORMAT_EAN_8,
                                        Barcode.FORMAT_UPC_A,
                                        Barcode.FORMAT_UPC_E
                                    )
                                    .build()
                                val scanner = BarcodeScanning.getClient(options)
                                activeScanner = scanner
                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()

                                activeAnalysis = imageAnalysis
                                imageAnalysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                                    val mediaImage = imageProxy.image
                                    if (mediaImage != null && !hasEmittedBarcode) {
                                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                        scanner.process(image)
                                            .addOnSuccessListener { barcodes ->
                                                for (barcode in barcodes) {
                                                    val rawValue = barcode.rawValue?.trim()
                                                    if (!disposed && !rawValue.isNullOrBlank() && !hasEmittedBarcode) {
                                                        hasEmittedBarcode = true
                                                        onBarcodeScanned(rawValue)
                                                        break
                                                    }
                                                }
                                            }
                                            .addOnCompleteListener {
                                                imageProxy.close()
                                            }
                                    } else {
                                        imageProxy.close()
                                    }
                                }

                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (_: Exception) {
                                // Ignore camera binding error on headless/unsupported environment
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("camera_preview_view")
                )

                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(20.dp)
                        .fillMaxWidth()
                        .border(1.dp, JuicyOrange.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = MutedSurface1.copy(alpha = 0.95f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = MangoYellow)
                            Text(
                                text = "Skieruj aparat na kod kreskowy produktu (EAN-13, EAN-8, UPC)",
                                color = Cream,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = manualBarcodeInput,
                                onValueChange = { manualBarcodeInput = it },
                                placeholder = { Text("Wpisz kod EAN/UPC...", fontSize = 12.sp, color = Cream.copy(alpha = 0.5f)) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("scanner_manual_barcode_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = JuicyOrange,
                                    unfocusedBorderColor = WarmBrown,
                                    focusedTextColor = Cream,
                                    unfocusedTextColor = Cream
                                )
                            )
                            Button(
                                onClick = {
                                    val code = manualBarcodeInput.trim()
                                    if (code.isNotEmpty() && !hasEmittedBarcode) {
                                        hasEmittedBarcode = true
                                        onBarcodeScanned(code)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = JuicyOrange, contentColor = MangoButtonInk),
                                modifier = Modifier.testTag("scanner_submit_barcode_button")
                            ) {
                                Text("Szukaj", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth()
                        .border(1.dp, WarmBrown, RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = MutedSurface1),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = MangoYellow,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "Wymagane zezwolenie na użycie aparatu w celu skanowania kodów (EAN-13, EAN-8, UPC).",
                            color = Cream,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        MangoPrimaryButton(
                            text = "Zezwól na aparat",
                            modifier = Modifier.testTag("request_camera_permission_button"),
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                        )
                        HorizontalDivider(color = WarmBrown.copy(alpha = 0.3f))
                        Text(
                            text = "Lub wpisz kod kreskowy ręcznie:",
                            color = GoldAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = manualBarcodeInput,
                                onValueChange = { manualBarcodeInput = it },
                                placeholder = { Text("Kod EAN-13 / EAN-8 / UPC", fontSize = 12.sp, color = Cream.copy(alpha = 0.5f)) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("scanner_manual_barcode_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = JuicyOrange,
                                    unfocusedBorderColor = WarmBrown,
                                    focusedTextColor = Cream,
                                    unfocusedTextColor = Cream
                                )
                            )
                            Button(
                                onClick = {
                                    val code = manualBarcodeInput.trim()
                                    if (code.isNotEmpty() && !hasEmittedBarcode) {
                                        hasEmittedBarcode = true
                                        onBarcodeScanned(code)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = JuicyOrange, contentColor = MangoButtonInk),
                                modifier = Modifier.testTag("scanner_submit_barcode_button")
                            ) {
                                Text("Szukaj", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
