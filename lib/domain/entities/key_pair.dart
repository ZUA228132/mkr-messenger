import 'dart:convert';
import 'dart:typed_data';

/// Represents a cryptographic key pair for X25519 key exchange
class KeyPair {
  final Uint8List publicKey;
  final Uint8List privateKey;

  const KeyPair({
    required this.publicKey,
    required this.privateKey,
  });

  /// Serializes the key pair to JSON string
  String toJson() {
    return jsonEncode({
      'publicKey': base64Encode(publicKey),
      'privateKey': base64Encode(privateKey),
    });
  }

  /// Deserializes a key pair from JSON string
  factory KeyPair.fromJson(String json) {
    final map = jsonDecode(json) as Map<String, dynamic>;
    return KeyPair(
      publicKey: base64Decode(map['publicKey'] as String),
      privateKey: base64Decode(map['privateKey'] as String),
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    if (other is! KeyPair) return false;
    if (publicKey.length != other.publicKey.length) return false;
    if (privateKey.length != other.privateKey.length) return false;
    for (int i = 0; i < publicKey.length; i++) {
      if (publicKey[i] != other.publicKey[i]) return false;
    }
    for (int i = 0; i < privateKey.length; i++) {
      if (privateKey[i] != other.privateKey[i]) return false;
    }
    return true;
  }

  @override
  int get hashCode => Object.hash(
        Object.hashAll(publicKey),
        Object.hashAll(privateKey),
      );
}
