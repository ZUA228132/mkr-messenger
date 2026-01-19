package com.pioneer.service

import java.util.*
import javax.mail.*
import javax.mail.internet.*

object EmailService {
    
    // Используем локальный Postfix
    private val smtpHost = System.getenv("SMTP_HOST") ?: "localhost"
    private val smtpPort = System.getenv("SMTP_PORT") ?: "25"
    private val fromEmail = System.getenv("FROM_EMAIL") ?: "noreply@kluboksrm.ru"
    private val fromName = "MKR Messenger"
    
    // Флаг использования аутентификации (для внешних SMTP)
    private val useAuth = System.getenv("SMTP_USER")?.isNotEmpty() == true
    private val smtpUser = System.getenv("SMTP_USER") ?: ""
    private val smtpPassword = System.getenv("SMTP_PASSWORD") ?: ""
    
    fun sendVerificationCode(toEmail: String, code: String): Boolean {
        return try {
            val subject = "Код подтверждения MKR"
            val body = """
                <!DOCTYPE html>
                <html>
                <head>
                    <style>
                        body { font-family: Arial, sans-serif; background: #1a0033; color: white; padding: 20px; }
                        .container { max-width: 500px; margin: 0 auto; background: linear-gradient(135deg, #2d0050, #1a0033); border-radius: 20px; padding: 30px; }
                        .logo { text-align: center; font-size: 48px; font-weight: bold; background: linear-gradient(90deg, #E040FB, #7C4DFF); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
                        .code { text-align: center; font-size: 36px; font-weight: bold; letter-spacing: 8px; color: #E040FB; margin: 30px 0; padding: 20px; background: rgba(255,255,255,0.1); border-radius: 10px; }
                        .text { text-align: center; color: #aaa; font-size: 14px; }
                        .footer { text-align: center; margin-top: 30px; color: #666; font-size: 12px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="logo">MKR</div>
                        <p class="text">Ваш код подтверждения:</p>
                        <div class="code">$code</div>
                        <p class="text">Код действителен 10 минут.<br>Если вы не запрашивали этот код, проигнорируйте это письмо.</p>
                        <div class="footer">© 2025 MKR Messenger</div>
                    </div>
                </body>
                </html>
            """.trimIndent()
            
            sendEmail(toEmail, subject, body, isHtml = true)
            true
        } catch (e: Exception) {
            println("EMAIL ERROR: Failed to send verification code to $toEmail: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    fun sendWelcomeEmail(toEmail: String, displayName: String): Boolean {
        return try {
            val subject = "Добро пожаловать в MKR!"
            val body = """
                <!DOCTYPE html>
                <html>
                <head>
                    <style>
                        body { font-family: Arial, sans-serif; background: #1a0033; color: white; padding: 20px; }
                        .container { max-width: 500px; margin: 0 auto; background: linear-gradient(135deg, #2d0050, #1a0033); border-radius: 20px; padding: 30px; }
                        .logo { text-align: center; font-size: 48px; font-weight: bold; background: linear-gradient(90deg, #E040FB, #7C4DFF); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
                        .welcome { text-align: center; font-size: 24px; margin: 20px 0; }
                        .text { text-align: center; color: #aaa; font-size: 14px; line-height: 1.6; }
                        .features { margin: 20px 0; padding: 20px; background: rgba(255,255,255,0.05); border-radius: 10px; }
                        .feature { padding: 10px 0; border-bottom: 1px solid rgba(255,255,255,0.1); }
                        .feature:last-child { border-bottom: none; }
                        .footer { text-align: center; margin-top: 30px; color: #666; font-size: 12px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="logo">MKR</div>
                        <div class="welcome">Привет, $displayName! 👋</div>
                        <p class="text">Добро пожаловать в MKR — защищённый мессенджер!</p>
                        <div class="features">
                            <div class="feature">🎬 Reels — короткие видео</div>
                            <div class="feature">📖 Stories — истории на 24 часа</div>
                            <div class="feature">🎵 Совместное прослушивание музыки</div>
                            <div class="feature">🔒 Секретные чаты с шифрованием</div>
                            <div class="feature">📺 Каналы для контента</div>
                        </div>
                        <p class="text">Приятного общения!</p>
                        <div class="footer">© 2025 MKR Messenger</div>
                    </div>
                </body>
                </html>
            """.trimIndent()
            
            sendEmail(toEmail, subject, body, isHtml = true)
            true
        } catch (e: Exception) {
            println("EMAIL ERROR: Failed to send welcome email to $toEmail: ${e.message}")
            false
        }
    }
    
    private fun sendEmail(to: String, subject: String, body: String, isHtml: Boolean = false) {
        val props = Properties().apply {
            put("mail.smtp.host", smtpHost)
            put("mail.smtp.port", smtpPort)
            put("mail.mime.charset", "UTF-8")
            put("mail.smtp.starttls.enable", "false")
            
            if (useAuth) {
                put("mail.smtp.auth", "true")
                put("mail.smtp.ssl.enable", "true")
                put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
            } else {
                put("mail.smtp.auth", "false")
            }
        }
        
        val session = if (useAuth) {
            Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(smtpUser, smtpPassword)
                }
            })
        } else {
            Session.getInstance(props)
        }
        
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(fromEmail, fromName, "UTF-8"))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
            setSubject(subject, "UTF-8")
            
            if (isHtml) {
                // Устанавливаем HTML контент напрямую с правильным Content-Type
                setContent(body, "text/html; charset=UTF-8")
                setHeader("Content-Type", "text/html; charset=UTF-8")
            } else {
                setText(body, "UTF-8")
            }
            
            // Дополнительные заголовки
            setHeader("X-Mailer", "MKR Messenger")
            setHeader("MIME-Version", "1.0")
            sentDate = java.util.Date()
        }
        
        Transport.send(message)
        println("EMAIL: Sent HTML email to $to - $subject")
    }
    
    fun generateVerificationCode(): String {
        return (100000..999999).random().toString()
    }
}
