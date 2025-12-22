package com.example.docscan.logic.ocr

import com.example.domain.interfaces.ocr.OcrGateway
import com.example.domain.types.ocr.OcrPolicy
import com.example.domain.types.ocr.OcrText
import com.example.ocr.core.api.OcrImage
import com.example.ocr.core.api.OcrPageResult
import com.example.docscan.logic.utils.NodeCloudOcrGateway

class OcrGatewayImpl(private val cloudOcrGateway: NodeCloudOcrGateway) : OcrGateway {
    override suspend fun recognize(
        docId: String,
        pageId: String,
        image: OcrImage,
        policy: OcrPolicy
    ): OcrGateway.Result {
        // For now, we will just use the cloud gateway.
        // In the future, you could add logic here to choose between different OCR engines based on the policy.

        val request = com.example.ocr.core.api.CloudOcrGateway.Request(
            image = image,
            lang = "vie+eng",
            hints = mapOf(
                "docId" to docId,
                "pageId" to pageId
            )
        )

        val response = cloudOcrGateway.recognize(request)

        val ocrText = OcrText(
            raw = response.text,
            // You may want to add logic here to clean or fold the text
            clean = response.text,
            folded = response.text.lowercase()
        )

        val ocrPageResult = OcrPageResult(
            pageNo = 0, // Assuming page number is 0 for now
            text = response.text,
            words = response.words,
            avgConf = response.words.mapNotNull { it.conf }.average().toFloat(),
            durationMs = response.elapsedMs
        )

        return OcrGateway.Result(
            page = ocrPageResult,
            text = ocrText,
            words = response.words
        )
    }
}
