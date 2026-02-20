package com.manish.demo.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

object EmailService {

    // ⚠️ REPLACE WITH YOUR DETAILS
    private const val SENDER_EMAIL = "movieflix241202@gmail.com"
    private const val SENDER_PASSWORD = "svpt koni ifnt oqf"

    suspend fun sendOtpEmail(recipientEmail: String, otp: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val props = Properties()
                props["mail.smtp.auth"] = "true"
                props["mail.smtp.starttls.enable"] = "true"
                props["mail.smtp.host"] = "smtp.gmail.com"
                props["mail.smtp.port"] = "587"

                val session = Session.getInstance(props, object : javax.mail.Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(SENDER_EMAIL, SENDER_PASSWORD)
                    }
                })

                val message = MimeMessage(session)
                message.setFrom(InternetAddress(SENDER_EMAIL))
                message.setRecipients(
                    Message.RecipientType.TO,
                    InternetAddress.parse(recipientEmail)
                )
                message.subject = "Your MovieFlix Verification Code"
                message.setText("Welcome to MovieFlix!\n\nYour OTP Code is: $otp\n\nPlease enter this code to complete your signup.")

                Transport.send(message)
                return@withContext true

            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext false
            }
        }
    }
}