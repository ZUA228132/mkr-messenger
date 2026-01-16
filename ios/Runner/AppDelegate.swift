import Flutter
import UIKit

@main
@objc class AppDelegate: FlutterAppDelegate {
  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    GeneratedPluginRegistrant.register(with: self)
    
    // Set up method channel for app icon management
    // Requirements: 5.1 - Change app icon to selected disguise
    let controller = window?.rootViewController as! FlutterViewController
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
    
    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }
}
