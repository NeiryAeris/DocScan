package com.example.ocr_remote

fun interface AuthTokenProvider {
    fun get(): String?
}