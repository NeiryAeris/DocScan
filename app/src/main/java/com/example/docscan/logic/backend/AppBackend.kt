package com.example.docscan.logic.backend

import com.example.docscan.auth.FirebaseIdTokenCache
import com.example.docscan.logic.ocr.OcrGatewayImpl
import com.example.docscan.logic.utils.NodeCloudOcrGateway
import com.example.ocr_remote.*

class AppBackend(
    baseUrl: String
) {
    // Token provider (sync)
    private val firebaseTokenProvider: () -> String? = { FirebaseIdTokenCache.get() }

    // --- Public (no auth)
    val ocrClient: RemoteOcrClient = RemoteOcrClientImpl(
        baseUrl = baseUrl,
        authTokenProvider = { null } // OCR does not need auth
    )

    val handwritingClient: RemoteHandwritingClient = RemoteHandwritingClientImpl(
        baseUrl = baseUrl
    )

    // --- Protected (Firebase)
    val documentsClient: RemoteDocumentsClient = RemoteDocumentsClientImpl(
        baseUrl = baseUrl,
        authTokenProvider = firebaseTokenProvider
    )

    val aiClient: RemoteAiClient = RemoteAiClientImpl(
        baseUrl = baseUrl,
        authTokenProvider = firebaseTokenProvider
    )

    val driveClient: RemoteDriveClient = RemoteDriveClientImpl(
        baseUrl = baseUrl,
        authTokenProvider = firebaseTokenProvider
    )

    // Domain gateway (used by existing UI already)
    val ocrGateway: OcrGatewayImpl = OcrGatewayImpl(
        cloudOcrGateway = NodeCloudOcrGateway(ocrClient)
    )
}
