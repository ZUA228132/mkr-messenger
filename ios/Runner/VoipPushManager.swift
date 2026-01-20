import Foundation
import PushKit
import CallKit
import Flutter
import AVFoundation

/// Manages VOIP push notifications and CallKit integration for incoming calls
/// Requirements: 8.4 - Use APNs for push notifications
/// Requirements: Support incoming calls when app is in background or terminated
@available(iOS 10.0, *)
class VoipPushManager: NSObject {
  static let shared = VoipPushManager()

  private var voipRegistry: PKPushRegistry?
  private var callController: CXCallController?
  private var callProvider: CXProvider?
  private var providerDelegate: ProviderDelegate?

  private var currentCallUuid: UUID?
  private var methodChannel: FlutterMethodChannel?

  private override init() {
    super.init()
    setupCallKit()
  }

  // MARK: - Setup

  func setup(methodChannel: FlutterMethodChannel) {
    self.methodChannel = methodChannel
    setupVoipPush()
  }

  private func setupCallKit() {
    let configuration = CXProviderConfiguration(localizedName: "MKR Messenger")
    configuration.supportsVideo = true
    configuration.maximumCallGroups = 1
    configuration.maximumCallsPerCallGroup = 1
    configuration.supportedHandleTypes = [.generic]
    if let appIcon = UIImage(named: "AppIcon") {
      configuration.iconTemplateImageData = appIcon.pngData()
    }

    // Ringtone
    if let ringtonePath = Bundle.main.path(forResource: "ringtone", ofType: "caf") {
      configuration.ringtoneSound = ringtonePath
    }

    providerDelegate = ProviderDelegate()
    providerDelegate?.onCallEnded = { [weak self] uuid in
      self?.currentCallUuid = nil
      self?.notifyFlutterCallEnded(uuid: uuid)
    }

    providerDelegate?.onCallAnswered = { [weak self] uuid in
      self?.notifyFlutterCallAnswered(uuid: uuid)
    }

    callProvider = CXProvider(configuration: configuration)
    callProvider?.setDelegate(providerDelegate, queue: nil)

    callController = CXCallController()

    print("CallKit setup completed")
  }

  private func setupVoipPush() {
    voipRegistry = PKPushRegistry(queue: DispatchQueue.main)
    voipRegistry?.delegate = self
    voipRegistry?.desiredPushTypes = [.voIP]

    print("VOIP push registry setup completed")
  }

  // MARK: - Incoming Call

  func reportIncomingCall(
    callerId: String,
    callerName: String,
    isVideo: Bool = false,
    roomId: String? = nil
  ) {
    let uuid = UUID()
    currentCallUuid = uuid

    let callHandle = CXHandle(type: .generic, value: callerId)

    let callUpdate = CXCallUpdate()
    callUpdate.remoteHandle = callHandle
    callUpdate.hasVideo = isVideo
    callUpdate.localizedCallerName = callerName

    callProvider?.reportNewIncomingCall(with: uuid, update: callUpdate) { error in
      if let error = error {
        print("Failed to report incoming call: \(error.localizedDescription)")
        self.notifyFlutterCallFailed(error: error.localizedDescription)
      } else {
        print("Incoming call reported successfully")
        self.notifyFlutterIncomingCall(
          uuid: uuid,
          callerId: callerId,
          callerName: callerName,
          isVideo: isVideo,
          roomId: roomId
        )
      }
    }
  }

  // MARK: - Outgoing Call

  func startOutgoingCall(
    callerId: String,
    isVideo: Bool = false,
    completion: @escaping (Bool) -> Void
  ) {
    let uuid = UUID()
    currentCallUuid = uuid

    let callHandle = CXHandle(type: .generic, value: callerId)

    let startCallAction = CXStartCallAction(call: uuid, handle: callHandle)
    startCallAction.isVideo = isVideo

    let transaction = CXTransaction(action: startCallAction)

    callController?.request(transaction) { error in
      if let error = error {
        print("Failed to start outgoing call: \(error.localizedDescription)")
        completion(false)
      } else {
        print("Outgoing call started successfully")
        completion(true)
      }
    }
  }

  // MARK: - End Call

  func endCall() {
    guard let uuid = currentCallUuid else {
      print("No active call to end")
      return
    }

    let endCallAction = CXEndCallAction(call: uuid)
    let transaction = CXTransaction(action: endCallAction)

    callController?.request(transaction) { error in
      if let error = error {
        print("Failed to end call: \(error.localizedDescription)")
      } else {
        print("Call ended successfully")
      }
    }
  }

  // MARK: - Flutter Communication

  private func notifyFlutterIncomingCall(
    uuid: UUID,
    callerId: String,
    callerName: String,
    isVideo: Bool,
    roomId: String?
  ) {
    methodChannel?.invokeMethod("onIncomingCall", arguments: [
      "uuid": uuid.uuidString,
      "callerId": callerId,
      "callerName": callerName,
      "isVideo": isVideo,
      "roomId": roomId ?? ""
    ])
  }

  private func notifyFlutterCallAnswered(uuid: UUID) {
    methodChannel?.invokeMethod("onCallAnswered", arguments: [
      "uuid": uuid.uuidString
    ])
  }

  private func notifyFlutterCallEnded(uuid: UUID) {
    methodChannel?.invokeMethod("onCallEnded", arguments: [
      "uuid": uuid.uuidString
    ])
  }

  private func notifyFlutterCallFailed(error: String) {
    methodChannel?.invokeMethod("onCallFailed", arguments: [
      "error": error
    ])
  }
}

// MARK: - PKPushRegistryDelegate

extension VoipPushManager: PKPushRegistryDelegate {
  func pushRegistry(
    _ registry: PKPushRegistry,
    didUpdate pushCredentials: PKPushCredentials,
    for type: PKPushType
  ) {
    let tokenParts = pushCredentials.token.map { String(format: "%02.2hhx", $0) }
    let token = tokenParts.joined()

    print("VOIP push token received: \(token)")

    // Send token to Flutter
    methodChannel?.invokeMethod("onVoipTokenReceived", arguments: [
      "token": token
    ])
  }

  func pushRegistry(
    _ registry: PKPushRegistry,
    didReceiveIncomingPushWith payload: PKPushPayload,
    for type: PKPushType
  ) {
    print("VOIP push received: \(payload.dictionaryPayload)")

    // Parse the payload
    let payloadDict = payload.dictionaryPayload

    guard let type = payloadDict["type"] as? String, type == "incoming_call" else {
      print("Not an incoming call push")
      return
    }

    let callerId = payloadDict["caller_id"] as? String ?? ""
    let callerName = payloadDict["caller_name"] as? String ?? "Unknown"
    let isVideo = payloadDict["is_video"] as? Bool ?? false
    let roomId = payloadDict["room_id"] as? String

    // Report incoming call via CallKit
    reportIncomingCall(
      callerId: callerId,
      callerName: callerName,
      isVideo: isVideo,
      roomId: roomId
    )
  }

  func pushRegistry(
    _ registry: PKPushRegistry,
    didInvalidatePushTokenFor type: PKPushType
  ) {
    print("VOIP push token invalidated")

    methodChannel?.invokeMethod("onVoipTokenInvalidated", arguments: nil)
  }
}

// MARK: - CXProviderDelegate

class ProviderDelegate: NSObject, CXProviderDelegate {
  var onCallEnded: ((UUID) -> Void)?
  var onCallAnswered: ((UUID) -> Void)?

  func providerDidReset(_ provider: CXProvider) {
    print("Provider did reset")
  }

  func provider(
    _ provider: CXProvider,
    perform action: CXAnswerCallAction
  ) {
    print("Call answered")
    onCallAnswered?(action.callUUID)
    action.fulfill()
  }

  func provider(
    _ provider: CXProvider,
    perform action: CXEndCallAction
  ) {
    print("Call ended")
    onCallEnded?(action.callUUID)
    action.fulfill()
  }

  func provider(
    _ provider: CXProvider,
    perform action: CXStartCallAction
  ) {
    print("Call started")
    action.fulfill()
  }

  func provider(
    _ provider: CXProvider,
    didActivate audioSession: AVAudioSession
  ) {
    print("Audio session activated")
    // Configure audio session for call
    do {
      try audioSession.setCategory(.playAndRecord, mode: .videoChat, options: [])
      try audioSession.setActive(true)
    } catch {
      print("Failed to configure audio session: \(error)")
    }
  }

  func provider(
    _ provider: CXProvider,
    didDeactivate audioSession: AVAudioSession
  ) {
    print("Audio session deactivated")
  }
}
