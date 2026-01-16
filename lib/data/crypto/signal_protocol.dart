import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';

import 'package:cryptography/cryptography.dart' as crypto;
import 'package:pointycastle/export.dart';

import '../../domain/entities/encrypted_message.dart';
import '../../domain/entities/key_pair.dart';

/// Implementation of Signal Protocol cryptographic operations
/// using X25519 for key exchange and AES-256-GCM for encryption
class SignalProtocol {
  static const int _nonceLength = 12; // 96 bits for GCM
  static const int _tagLength = 16; // 128 bits authentication tag
  static const int _keyLength = 32; // 256 bits for AES-256

  final SecureRandom _secureRandom;
  final crypto.X25519 _x25519;

  SignalProtocol()
      : _secureRandom = _createSecureRandom(),
        _x25519 = crypto.X25519();

  static SecureRandom _createSecureRandom() {
    final secureRandom = FortunaRandom();
    final random = Random.secure();
    final seeds = List<int>.generate(32, (_) => random.nextInt(256));
    secureRandom.seed(KeyParameter(Uint8List.fromList(seeds)));
    return secureRandom;
  }

  /// Generates a new X25519 key pair for identity/session keys
  /// Requirements: 3.1 - X25519 key exchange
  Future<KeyPair> generateKeyPair() async {
    final keyPair = await _x25519.newKeyPair();
    final publicKey = await keyPair.extractPublicKey();
    final privateKeyBytes = await keyPair.extractPrivateKeyBytes();

    return KeyPair(
      publicKey: Uint8List.fromList(publicKey.bytes),
      privateKey: Uint8List.fromList(privateKeyBytes),
    );
  }

  /// Performs X25519 key exchange to derive a shared secret
  /// Requirements: 3.1 - X25519 key exchange
  Future<Uint8List> performKeyExchange(
    Uint8List ourPrivateKey,
    Uint8List theirPublicKey,
  ) async {
    final privateKey = await _x25519.newKeyPairFromSeed(ourPrivateKey);
    final publicKey = crypto.SimplePublicKey(
      theirPublicKey,
      type: crypto.KeyPairType.x25519,
    );

    final sharedSecret = await _x25519.sharedSecretKey(
      keyPair: privateKey,
      remotePublicKey: publicKey,
    );

    return Uint8List.fromList(await sharedSecret.extractBytes());
  }


  /// Derives a session key from shared secret using HKDF
  Uint8List deriveSessionKey(
    Uint8List sharedSecret, {
    Uint8List? salt,
    Uint8List? info,
  }) {
    final hkdf = HKDFKeyDerivator(SHA256Digest());
    final params = HkdfParameters(
      sharedSecret,
      _keyLength,
      salt,
      info,
    );
    hkdf.init(params);

    final sessionKey = Uint8List(_keyLength);
    hkdf.deriveKey(null, 0, sessionKey, 0);
    return sessionKey;
  }

  /// Encrypts plaintext using AES-256-GCM with a unique nonce
  /// Requirements: 3.2 - AES-256-GCM with unique key per message
  /// Requirements: 3.3 - Decrypt and verify message integrity
  EncryptedMessage encrypt(String plaintext, Uint8List sessionKey) {
    final plaintextBytes = Uint8List.fromList(utf8.encode(plaintext));
    return encryptBytes(plaintextBytes, sessionKey);
  }

  /// Encrypts raw bytes using AES-256-GCM
  EncryptedMessage encryptBytes(Uint8List plaintext, Uint8List sessionKey) {
    // Generate unique nonce for each message
    final nonce = _generateNonce();

    final cipher = GCMBlockCipher(AESEngine());
    final params = AEADParameters(
      KeyParameter(sessionKey),
      _tagLength * 8, // tag length in bits
      nonce,
      Uint8List(0), // no additional authenticated data
    );

    cipher.init(true, params); // true = encrypt

    // For GCM encryption: output = ciphertext + tag
    final outputSize = cipher.getOutputSize(plaintext.length);
    final output = Uint8List(outputSize);
    
    var len = cipher.processBytes(plaintext, 0, plaintext.length, output, 0);
    len += cipher.doFinal(output, len);

    // The actual output length after doFinal
    final actualOutputLength = len;
    
    // Split ciphertext and tag
    final ciphertextLength = actualOutputLength - _tagLength;
    final ciphertext = Uint8List(ciphertextLength);
    final tag = Uint8List(_tagLength);
    
    ciphertext.setRange(0, ciphertextLength, output);
    tag.setRange(0, _tagLength, output, ciphertextLength);

    return EncryptedMessage(
      ciphertext: ciphertext,
      nonce: nonce,
      tag: tag,
    );
  }


  /// Decrypts an encrypted message using AES-256-GCM
  /// Requirements: 3.3 - Decrypt and verify message integrity
  String decrypt(EncryptedMessage encrypted, Uint8List sessionKey) {
    final decryptedBytes = decryptBytes(encrypted, sessionKey);
    return utf8.decode(decryptedBytes);
  }

  /// Decrypts raw bytes using AES-256-GCM
  Uint8List decryptBytes(EncryptedMessage encrypted, Uint8List sessionKey) {
    final cipher = GCMBlockCipher(AESEngine());
    final params = AEADParameters(
      KeyParameter(sessionKey),
      _tagLength * 8,
      encrypted.nonce,
      Uint8List(0),
    );

    cipher.init(false, params); // false = decrypt

    // Combine ciphertext and tag for decryption
    final ciphertextWithTag = Uint8List(
      encrypted.ciphertext.length + encrypted.tag.length,
    );
    ciphertextWithTag.setRange(0, encrypted.ciphertext.length, encrypted.ciphertext);
    ciphertextWithTag.setRange(
      encrypted.ciphertext.length,
      ciphertextWithTag.length,
      encrypted.tag,
    );

    // For GCM decryption: output = plaintext (tag is verified internally)
    final outputSize = cipher.getOutputSize(ciphertextWithTag.length);
    final output = Uint8List(outputSize);
    
    var len = cipher.processBytes(ciphertextWithTag, 0, ciphertextWithTag.length, output, 0);
    len += cipher.doFinal(output, len);

    // Return only the actual plaintext bytes
    return Uint8List.fromList(output.sublist(0, len));
  }

  /// Generates a cryptographically secure random nonce
  Uint8List _generateNonce() {
    return _secureRandom.nextBytes(_nonceLength);
  }

  /// Serializes a KeyPair to JSON string
  /// Requirements: 3.4 - JSON encoding for key storage
  String serializeKeys(KeyPair keys) {
    return keys.toJson();
  }

  /// Deserializes a KeyPair from JSON string
  /// Requirements: 3.5 - Parse JSON and restore key objects
  KeyPair deserializeKeys(String json) {
    return KeyPair.fromJson(json);
  }
}
