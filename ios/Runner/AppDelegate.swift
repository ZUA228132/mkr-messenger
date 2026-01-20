import Flutter
import UIKit
import Firebase
import FirebaseMessaging
import PushKit
import CallKit
import AVFoundation
import UserNotifications

@main
@objc class AppDelegate: FlutterAppDelegate {
  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    // Initialize Firebase
    FirebaseApp.configure()

    GeneratedPluginRegistrant.register(with: self)

    // Set up method channels
    let controller = window?.rootViewController as! FlutterViewController

    // App icon management channel
    setupAppIconChannel(controller: controller)

    // Push notification channel
    setupPushNotificationChannel(controller: controller)

    // Set delegate for remote notifications
    UNUserNotificationCenter.current().delegate = self

    // Set FCM messaging delegate
    Messaging.messaging().delegate = self

    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }

  // MARK: - App Icon Channel

  private func setupAppIconChannel(controller: FlutterViewController) {
    let appIconChannel = FlutterMethodChannel(
      name: "com.mkr.messenger/app_icon",
      binaryMessenger: controller.binaryMessenger
    )

    appIconChannel.setMethodCallHandler { [weak self] (call, result) in
      switch call.method {
      case "supportsAlternateIcons":
        if #available(iOS 10.3, *) {
          result(UIApplication.shared.supportsAlternateIcons)
        } else {
          result(false)
        }

      case "getAlternateIconName":
        if #available(iOS 10.3, *) {
          result(UIApplication.shared.alternateIconName)
        } else {
          result(nil)
        }

      case "setAlternateIconName":
        if #available(iOS 10.3, *) {
          guard let args = call.arguments as? [String: Any?] else {
            result(FlutterError(code: "INVALID_ARGS", message: "Invalid arguments", details: nil))
            return
          }
          let iconName = args["iconName"] as? String

          UIApplication.shared.setAlternateIconName(iconName) { error in
            if let error = error {
              result(FlutterError(code: "ICON_ERROR", message: error.localizedDescription, details: nil))
            } else {
              result(nil)
            }
          }
        } else {
          result(FlutterError(code: "UNSUPPORTED", message: "Alternate icons not supported", details: nil))
        }

      default:
        result(FlutterMethodNotImplemented)
      }
    }
  }

  // MARK: - Push Notification Channel

  private func setupPushNotificationChannel(controller: FlutterViewController) {
    let pushChannel = FlutterMethodChannel(
      name: "com.mkr.messenger/push_notification",
      binaryMessenger: controller.binaryMessenger
    )

    pushChannel.setMethodCallHandler { [weak self] (call, result) in
      guard let self = self else {
        result(FlutterError(code: "UNAVAILABLE", message: "AppDelegate unavailable", details: nil))
        return
      }

      switch call.method {
      case "getAPNsToken":
        if let token = self.apnsToken {
          result(token)
        } else {
          result(nil)
        }

      case "registerForRemoteNotifications":
        self.registerForRemoteNotifications()
        result(nil)

      case "reportIncomingCall":
        guard let args = call.arguments as? [String: Any?] else {
          result(FlutterError(code: "INVALID_ARGS", message: "Invalid arguments", details: nil))
          return
        }
        let callerId = args["callerId"] as? String ?? ""
        let callerName = args["callerName"] as? String ?? "Unknown"
        let isVideo = args["isVideo"] as? Bool ?? false
        let roomId = args["roomId"] as? String

        if #available(iOS 10.0, *) {
          VoipPushManager.shared.reportIncomingCall(
            callerId: callerId,
            callerName: callerName,
            isVideo: isVideo,
            roomId: roomId
          )
          result(nil)
        } else {
          result(FlutterError(code: "UNSUPPORTED", message: "VOIP not supported on this iOS version", details: nil))
        }

      case "startOutgoingCall":
        guard let args = call.arguments as? [String: Any?] else {
          result(FlutterError(code: "INVALID_ARGS", message: "Invalid arguments", details: nil))
          return
        }
        let callerId = args["callerId"] as? String ?? ""
        let isVideo = args["isVideo"] as? Bool ?? false

        if #available(iOS 10.0, *) {
          VoipPushManager.shared.startOutgoingCall(callerId: callerId, isVideo: isVideo) { success in
            result(success)
          }
        } else {
          result(FlutterError(code: "UNSUPPORTED", message: "VOIP not supported on this iOS version", details: nil))
        }

      case "endCall":
        if #available(iOS 10.0, *) {
          VoipPushManager.shared.endCall()
          result(nil)
        } else {
          result(FlutterError(code: "UNSUPPORTED", message: "VOIP not supported on this iOS version", details: nil))
        }

      default:
        result(FlutterMethodNotImplemented)
      }
    }

    // Setup VOIP manager with this channel
    if #available(iOS 10.0, *) {
      VoipPushManager.shared.setup(methodChannel: pushChannel)
    }
  }

  // MARK: - APNs Token

  private var apnsToken: String?

  // Called when APNs token is successfully registered
  override func application(
    _ application: UIApplication,
    didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
  ) {
    let tokenParts = deviceToken.map { data in String(format: "%02.2hhx", data) }
    let token = tokenParts.joined()
    apnsToken = token

    print("APNs Token registered: \(token)")

    // Send to FCM
    Messaging.messaging().apnsToken = deviceToken

    // Notify Flutter
    let controller = window?.rootViewController as! FlutterViewController
    let pushChannel = FlutterMethodChannel(
      name: "com.mkr.messenger/push_notification",
      binaryMessenger: controller.binaryMessenger
    )
    pushChannel.invokeMethod("onAPNsTokenReceived", arguments: ["token": token])
  }

  // Called when APNs token fails to register
  override func application(
    _ application: UIApplication,
    didFailToRegisterForRemoteNotificationsWithError error: Error
  ) {
    print("Failed to register for remote notifications: \(error)")

    let controller = window?.rootViewController as! FlutterViewController
    let pushChannel = FlutterMethodChannel(
      name: "com.mkr.messenger/push_notification",
      binaryMessenger: controller.binaryMessenger
    )
    pushChannel.invokeMethod("onAPNsTokenError", arguments: ["error": error.localizedDescription])
  }

  private func registerForRemoteNotifications() {
    UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
      if granted {
        print("Notification permission granted")
        DispatchQueue.main.async {
          UIApplication.shared.registerForRemoteNotifications()
        }
      } else {
        print("Notification permission denied: \(error?.localizedDescription ?? "")")
      }
    }
  }
}

// MARK: - UNUserNotificationCenterDelegate

extension AppDelegate: UNUserNotificationCenterDelegate {
  // Called when app is in foreground and a notification is received
  func userNotificationCenter(
    _ center: UNUserNotificationCenter,
    willPresent notification: UNNotification,
    withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
  ) {
    let userInfo = notification.request.content.userInfo

    print("Notification received in foreground: \(userInfo)")

    // Notify Flutter about the notification
    let controller = window?.rootViewController as! FlutterViewController
    let pushChannel = FlutterMethodChannel(
      name: "com.mkr.messenger/push_notification",
      binaryMessenger: controller.binaryMessenger
    )

    if let messageType = userInfo["type"] as? String {
      pushChannel.invokeMethod("onNotificationReceived", arguments: [
        "type": messageType,
        "data": userInfo
      ])
    }

    // Show notification in foreground (iOS 14+)
    if #available(iOS 14.0, *) {
      completionHandler([.banner, .sound, .badge])
    } else {
      completionHandler([.alert, .sound, .badge])
    }
  }

  // Called when user taps on notification
  func userNotificationCenter(
    _ center: UNUserNotificationCenter,
    didReceive response: UNNotificationResponse,
    withCompletionHandler completionHandler: @escaping () -> Void
  ) {
    let userInfo = response.notification.request.content.userInfo

    print("Notification tapped: \(userInfo)")

    // Notify Flutter about the notification tap
    let controller = window?.rootViewController as! FlutterViewController
    let pushChannel = FlutterMethodChannel(
      name: "com.mkr.messenger/push_notification",
      binaryMessenger: controller.binaryMessenger
    )

    pushChannel.invokeMethod("onNotificationTapped", arguments: [
      "data": userInfo,
      "actionIdentifier": response.actionIdentifier
    ])

    completionHandler()
  }
}

// MARK: - MessagingDelegate

extension AppDelegate: MessagingDelegate {
  // Called when FCM token is refreshed
  func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
    print("FCM token received or refreshed: \(fcmToken ?? "nil")")

    let controller = window?.rootViewController as! FlutterViewController
    let pushChannel = FlutterMethodChannel(
      name: "com.mkr.messenger/push_notification",
      binaryMessenger: controller.binaryMessenger
    )

    if let token = fcmToken {
      pushChannel.invokeMethod("onFCMTokenReceived", arguments: ["token": token])
    }
  }

  // Called when FCM receives data message (direct channel, not through APNs)
  func messaging(_ messaging: Messaging, didReceive remoteMessage: RemoteMessage) {
    print("FCM data message received: \(remoteMessage.appData)")
  }
}
