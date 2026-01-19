package com.pranavajay.utils

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.smithy.kotlin.runtime.content.ByteStream
import io.ktor.server.application.*
import java.util.*

object S3Utils {
    private lateinit var bucketName: String
    private lateinit var region: String
    private var s3Client: S3Client? = null
    
    fun init(environment: ApplicationEnvironment) {
        bucketName = environment.config.propertyOrNull("aws.bucketName")?.getString() ?: ""
        region = environment.config.propertyOrNull("aws.region")?.getString() ?: "eu-north-1"
        
        if (bucketName.isNotEmpty()) {
            s3Client = S3Client { this.region = this@S3Utils.region }
        }
    }
    
    suspend fun uploadFile(bytes: ByteArray, fileName: String, contentType: String): String? {
        val client = s3Client ?: return null
        
        val key = "${UUID.randomUUID()}_$fileName"
        
        try {
            client.putObject(PutObjectRequest {
                bucket = bucketName
                this.key = key
                body = ByteStream.fromBytes(bytes)
                this.contentType = contentType
            })
            
            return "https://$bucketName.s3.$region.amazonaws.com/$key"
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
