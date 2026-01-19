package com.pranavajay.routes

import com.pranavajay.models.UploadResponse
import com.pranavajay.utils.S3Utils
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.uploadRoutes() {
    authenticate("auth-jwt") {
        route("/api/upload") {
            post {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                    return@post
                }
                
                val multipart = call.receiveMultipart()
                var fileUrl: String? = null
                var fileKey: String? = null
                
                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            val fileName = part.originalFileName ?: "file_${UUID.randomUUID()}"
                            val contentType = part.contentType?.toString() ?: "application/octet-stream"
                            val bytes = part.streamProvider().readBytes()
                            
                            fileUrl = S3Utils.uploadFile(bytes, fileName, contentType)
                            fileKey = fileName
                        }
                        else -> {}
                    }
                    part.dispose()
                }
                
                if (fileUrl != null) {
                    call.respond(UploadResponse(url = fileUrl!!, key = fileKey ?: ""))
                } else {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Upload failed"))
                }
            }
        }
    }
}
