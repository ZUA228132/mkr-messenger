//
//  MKRConfig.swift
//  MKR Messaging
//
//  Configuration for MKR branded messaging servers
//

import Foundation

public struct MKRConfig {

    // MARK: - Server Configuration
    /// MKR Server Configuration
    /// Update these values to point to your MKR infrastructure

    /// IMAP Server Configuration (for receiving messages)
    public static let imapServer = "imap.mkr.su"
    public static let imapPort = 993
    public static let imapSecurity = "ssl"  // Options: "automatic", "ssl", "starttls", "plain"

    /// SMTP Server Configuration (for sending messages)
    public static let smtpServer = "smtp.mkr.su"
    public static let smtpPort = 465
    public static let smtpSecurity = "ssl"  // Options: "automatic", "ssl", "starttls", "plain"

    /// WebRTC/Signaling Server Configuration (for video/voice calls)
    public static let webrtcSignalingServer = "wss://signaling.mkr.su"
    public static let webrtcTurnServer = "turn.mkr.su"
    public static let webrtcStunServer = "stun.mkr.su:3478"

    /// Media Storage Configuration
    public static let mediaStorageServer = "https://media.mkr.su"
    public static let mediaUploadEndpoint = "\(mediaStorageServer)/api/upload"
    public static let mediaDownloadEndpoint = "\(mediaStorageServer)/api/download"

    /// MARK: - Brand Configuration
    public static let brandName = "MKR"
    public static let brandDomain = "mkr.su"
    public static let supportEmail = "support@mkr.su"

    /// MARK: - Feature Flags
    public static let enableVoiceCalls = true
    public static let enableVideoCalls = true
    public static let enableE2EEncryption = true
    public static let enableReadReceipts = true
    public static let enableTypingIndicators = true

    /// MARK: - Default Account Settings
    /// When users sign up with @mkr.su email, use these defaults
    public static func getDefaultLoginParams(for email: String) -> (imapServer: String, imapPort: Int, smtpServer: String, smtpPort: Int) {
        // If user has @mkr.su email, use MKR servers
        if email.hasSuffix("@mkr.su") {
            return (imapServer, imapPort, smtpServer, smtpPort)
        }
        // Otherwise let user configure manually or use auto-discovery
        return ("", 0, "", 0)
    }

    /// MARK: - Help & Support URLs
    public static let helpURL = "https://help.mkr.su"
    public static let privacyPolicyURL = "https://mkr.su/privacy"
    public static let termsOfServiceURL = "https://mkr.su/terms"
    public static let imprintURL = "https://mkr.su/imprint"

    // MARK: - Provider Information
    /// Returns provider information for MKR email addresses
    public static let providerInfo = """
    MKR Messaging Service

    Welcome to MKR! Your messages are secured with end-to-end encryption.

    For support, visit: \(helpURL)
    """
}
