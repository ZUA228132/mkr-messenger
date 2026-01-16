import 'dart:typed_data';

/// Represents an encrypted message with all necessary components for decryption
class EncryptedMessage {
  /// The encrypted content
  final Uint8List ciphertext;
  
  /// Unique nonce (initialization vector) used for this encryption
  final Uint8List nonce;
  
  /// Authentication tag for integrity verification
  final Uint8List tag;

  const EncryptedMessage({
    required this.ciphertext,
    required this.nonce,
    required this.tag,
  });

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    if (other is! EncryptedMessage) return false;
    if (ciphertext.length != other.ciphertext.length) return false;
    if (nonce.length != other.nonce.length) return false;
    if (tag.length != other.tag.length) return false;
    for (int i = 0; i < ciphertext.length; i++) {
      if (ciphertext[i] != other.ciphertext[i]) return false;
    }
    for (int i = 0; i < nonce.length; i++) {
      if (nonce[i] != other.nonce[i]) return false;
    }
    for (int i = 0; i < tag.length; i++) {
      if (tag[i] != other.tag[i]) return false;
    }
    return true;
  }

  @override
  int get hashCode => Object.hash(
        Object.hashAll(ciphertext),
        Object.hashAll(nonce),
        Object.hashAll(tag),
      );
}
