package com.pioneer.routes

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.io.File
import java.util.*

@Serializable
data class UploadResponse(
    val url: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String?
)

fun Route.fileRoutes() {
    // Старый роут для совместимости с Android клиентом
    route("/api/upload") {
        authenticate("auth-jwt") {
            post("/avatar") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val multipart = call.receiveMultipart()
                var uploadedFile: UploadResponse? = null
                
                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            val uniqueName = "${UUID.randomUUID()}.jpg"
                            
                            val uploadDir = File("/root/uploads/avatars")
                            if (!uploadDir.exists()) uploadDir.mkdirs()
                            
                            val file = File(uploadDir, uniqueName)
                            part.streamProvider().use { input ->
                                file.outputStream().buffered().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            
                            uploadedFile = UploadResponse(
                                url = "/uploads/avatars/$uniqueName",
                                fileName = uniqueName,
                                fileSize = file.length(),
                                mimeType = "image/jpeg"
                            )
                            
                            println("FILE: Uploaded avatar -> /uploads/avatars/$uniqueName (${file.length()} bytes)")
                        }
                        else -> {}
                    }
                    part.dispose()
                }
                
                if (uploadedFile != null) {
                    call.respond(HttpStatusCode.Created, uploadedFile!!)
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No file uploaded"))
                }
            }
            
            // Загрузка изображения
            post("/image") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val multipart = call.receiveMultipart()
                var uploadedFile: UploadResponse? = null
                
                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            val uniqueName = "${UUID.randomUUID()}.jpg"
                            
                            val uploadDir = File("/root/uploads/images")
                            if (!uploadDir.exists()) uploadDir.mkdirs()
                            
                            val file = File(uploadDir, uniqueName)
                            part.streamProvider().use { input ->
                                file.outputStream().buffered().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            
                            uploadedFile = UploadResponse(
                                url = "/uploads/images/$uniqueName",
                                fileName = uniqueName,
                                fileSize = file.length(),
                                mimeType = "image/jpeg"
                            )
                            
                            println("FILE: Uploaded image -> /uploads/images/$uniqueName (${file.length()} bytes)")
                        }
                        else -> {}
                    }
                    part.dispose()
                }
                
                if (uploadedFile != null) {
                    call.respond(HttpStatusCode.Created, uploadedFile!!)
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No file uploaded"))
                }
            }
            
            // Загрузка видео
            post("/video") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val multipart = call.receiveMultipart()
                var uploadedFile: UploadResponse? = null
                
                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            val uniqueName = "${UUID.randomUUID()}.mp4"
                            
                            val uploadDir = File("/root/uploads/videos")
                            if (!uploadDir.exists()) uploadDir.mkdirs()
                            
                            val file = File(uploadDir, uniqueName)
                            part.streamProvider().use { input ->
                                file.outputStream().buffered().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            
                            uploadedFile = UploadResponse(
                                url = "/uploads/videos/$uniqueName",
                                fileName = uniqueName,
                                fileSize = file.length(),
                                mimeType = "video/mp4"
                            )
                            
                            println("FILE: Uploaded video -> /uploads/videos/$uniqueName (${file.length()} bytes)")
                        }
                        else -> {}
                    }
                    part.dispose()
                }
                
                if (uploadedFile != null) {
                    call.respond(HttpStatusCode.Created, uploadedFile!!)
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No file uploaded"))
                }
            }
            
            // Загрузка голосового
            post("/voice") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val multipart = call.receiveMultipart()
                var uploadedFile: UploadResponse? = null
                
                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            val uniqueName = "${UUID.randomUUID()}.m4a"
                            
                            val uploadDir = File("/root/uploads/voice")
                            if (!uploadDir.exists()) uploadDir.mkdirs()
                            
                            val file = File(uploadDir, uniqueName)
                            part.streamProvider().use { input ->
                                file.outputStream().buffered().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            
                            uploadedFile = UploadResponse(
                                url = "/uploads/voice/$uniqueName",
                                fileName = uniqueName,
                                fileSize = file.length(),
                                mimeType = "audio/mp4"
                            )
                            
                            println("FILE: Uploaded voice -> /uploads/voice/$uniqueName (${file.length()} bytes)")
                        }
                        else -> {}
                    }
                    part.dispose()
                }
                
                if (uploadedFile != null) {
                    call.respond(HttpStatusCode.Created, uploadedFile!!)
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No file uploaded"))
                }
            }
            
            // Загрузка файла
            post("/file") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val multipart = call.receiveMultipart()
                var uploadedFile: UploadResponse? = null
                
                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            val fileName = part.originalFileName ?: "file_${System.currentTimeMillis()}"
                            val fileExtension = fileName.substringAfterLast(".", "bin")
                            val uniqueName = "${UUID.randomUUID()}.$fileExtension"
                            
                            val uploadDir = File("/root/uploads/files")
                            if (!uploadDir.exists()) uploadDir.mkdirs()
                            
                            val file = File(uploadDir, uniqueName)
                            part.streamProvider().use { input ->
                                file.outputStream().buffered().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            
                            uploadedFile = UploadResponse(
                                url = "/uploads/files/$uniqueName",
                                fileName = fileName,
                                fileSize = file.length(),
                                mimeType = part.contentType?.toString()
                            )
                            
                            println("FILE: Uploaded file -> /uploads/files/$uniqueName (${file.length()} bytes)")
                        }
                        else -> {}
                    }
                    part.dispose()
                }
                
                if (uploadedFile != null) {
                    call.respond(HttpStatusCode.Created, uploadedFile!!)
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No file uploaded"))
                }
            }
        }
    }
    
    route("/api/files") {
        authenticate("auth-jwt") {
            // Загрузка аватара
            post("/upload/avatar") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val multipart = call.receiveMultipart()
                var uploadedFile: UploadResponse? = null
                
                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            val uniqueName = "${UUID.randomUUID()}.jpg"
                            
                            val uploadDir = File("/root/uploads/avatars")
                            if (!uploadDir.exists()) uploadDir.mkdirs()
                            
                            val file = File(uploadDir, uniqueName)
                            part.streamProvider().use { input ->
                                file.outputStream().buffered().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            
                            uploadedFile = UploadResponse(
                                url = "/uploads/avatars/$uniqueName",
                                fileName = uniqueName,
                                fileSize = file.length(),
                                mimeType = "image/jpeg"
                            )
                            
                            println("FILE: Uploaded avatar -> /uploads/avatars/$uniqueName (${file.length()} bytes)")
                        }
                        else -> {}
                    }
                    part.dispose()
                }
                
                if (uploadedFile != null) {
                    call.respond(HttpStatusCode.Created, uploadedFile!!)
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No file uploaded"))
                }
            }
            
            // Загрузка файла
            post("/upload") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val multipart = call.receiveMultipart()
                var uploadedFile: UploadResponse? = null
                
                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            val fileName = part.originalFileName ?: "file_${System.currentTimeMillis()}"
                            val fileExtension = fileName.substringAfterLast(".", "")
                            val uniqueName = "${UUID.randomUUID()}.$fileExtension"
                            
                            // Определяем директорию по типу файла
                            val subDir = when {
                                fileExtension in listOf("mp4", "webm", "mov") -> "videos"
                                fileExtension in listOf("mp3", "ogg", "wav", "m4a") -> "audio"
                                fileExtension in listOf("jpg", "jpeg", "png", "gif", "webp") -> "images"
                                else -> "files"
                            }
                            
                            val uploadDir = File("/root/uploads/$subDir")
                            if (!uploadDir.exists()) uploadDir.mkdirs()
                            
                            val file = File(uploadDir, uniqueName)
                            part.streamProvider().use { input ->
                                file.outputStream().buffered().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            
                            val mimeType = part.contentType?.toString()
                            
                            uploadedFile = UploadResponse(
                                url = "/uploads/$subDir/$uniqueName",
                                fileName = fileName,
                                fileSize = file.length(),
                                mimeType = mimeType
                            )
                            
                            println("FILE: Uploaded $fileName -> /uploads/$subDir/$uniqueName (${file.length()} bytes)")
                        }
                        else -> {}
                    }
                    part.dispose()
                }
                
                if (uploadedFile != null) {
                    call.respond(HttpStatusCode.Created, uploadedFile!!)
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No file uploaded"))
                }
            }
            
            // Загрузка видеокружка
            post("/upload/video-note") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val multipart = call.receiveMultipart()
                var uploadedFile: UploadResponse? = null
                
                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            val uniqueName = "${UUID.randomUUID()}.mp4"
                            
                            val uploadDir = File("/root/uploads/video_notes")
                            if (!uploadDir.exists()) uploadDir.mkdirs()
                            
                            val file = File(uploadDir, uniqueName)
                            part.streamProvider().use { input ->
                                file.outputStream().buffered().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            
                            uploadedFile = UploadResponse(
                                url = "/uploads/video_notes/$uniqueName",
                                fileName = uniqueName,
                                fileSize = file.length(),
                                mimeType = "video/mp4"
                            )
                            
                            println("FILE: Uploaded video note -> /uploads/video_notes/$uniqueName (${file.length()} bytes)")
                        }
                        else -> {}
                    }
                    part.dispose()
                }
                
                if (uploadedFile != null) {
                    call.respond(HttpStatusCode.Created, uploadedFile!!)
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No file uploaded"))
                }
            }
            
            // Загрузка голосового сообщения
            post("/upload/voice") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val multipart = call.receiveMultipart()
                var uploadedFile: UploadResponse? = null
                
                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            val uniqueName = "${UUID.randomUUID()}.m4a"
                            
                            val uploadDir = File("/root/uploads/voice")
                            if (!uploadDir.exists()) uploadDir.mkdirs()
                            
                            val file = File(uploadDir, uniqueName)
                            part.streamProvider().use { input ->
                                file.outputStream().buffered().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            
                            uploadedFile = UploadResponse(
                                url = "/uploads/voice/$uniqueName",
                                fileName = uniqueName,
                                fileSize = file.length(),
                                mimeType = "audio/mp4"
                            )
                            
                            println("FILE: Uploaded voice -> /uploads/voice/$uniqueName (${file.length()} bytes)")
                        }
                        else -> {}
                    }
                    part.dispose()
                }
                
                if (uploadedFile != null) {
                    call.respond(HttpStatusCode.Created, uploadedFile!!)
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No file uploaded"))
                }
            }
            
            // Загрузка Reels видео
            post("/upload/reel") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val multipart = call.receiveMultipart()
                var uploadedFile: UploadResponse? = null
                var description: String? = null
                
                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            val uniqueName = "${UUID.randomUUID()}.mp4"
                            
                            val uploadDir = File("/root/uploads/reels")
                            if (!uploadDir.exists()) uploadDir.mkdirs()
                            
                            val file = File(uploadDir, uniqueName)
                            part.streamProvider().use { input ->
                                file.outputStream().buffered().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            
                            uploadedFile = UploadResponse(
                                url = "/uploads/reels/$uniqueName",
                                fileName = uniqueName,
                                fileSize = file.length(),
                                mimeType = "video/mp4"
                            )
                            
                            println("FILE: Uploaded reel -> /uploads/reels/$uniqueName (${file.length()} bytes)")
                        }
                        is PartData.FormItem -> {
                            if (part.name == "description") {
                                description = part.value
                            }
                        }
                        else -> {}
                    }
                    part.dispose()
                }
                
                if (uploadedFile != null) {
                    call.respond(HttpStatusCode.Created, uploadedFile!!)
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No file uploaded"))
                }
            }
        }
        
        // Скачивание файла (без авторизации для простоты)
        get("/download/{path...}") {
            val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
            val file = File("/root/uploads/$path")
            
            if (file.exists() && file.isFile) {
                call.respondFile(file)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}
