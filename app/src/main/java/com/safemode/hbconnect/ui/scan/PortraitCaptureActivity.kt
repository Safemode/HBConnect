package com.safemode.hbconnect.ui.scan

import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView

/**
 * A [CaptureActivity] locked to portrait (via `screenOrientation` in the manifest) so the scanner
 * doesn't flip to landscape. It also nudges the camera toward a sharper read: continuous autofocus
 * plus scene-mode and metering where the device supports them. The underlying zxing-android-embedded
 * library uses the legacy Camera API, so this is the practical ceiling for focus/quality without
 * moving to a CameraX-based scanner.
 */
class PortraitCaptureActivity : CaptureActivity() {
    override fun initializeContent(): DecoratedBarcodeView {
        val view = super.initializeContent()
        view.barcodeView.cameraSettings.apply {
            isAutoFocusEnabled = true
            isContinuousFocusEnabled = true
            isBarcodeSceneModeEnabled = true
            isMeteringEnabled = true
        }
        return view
    }
}
