import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/foundation.dart';
import 'package:path/path.dart' as path;

import '../../domain/entities/encrypted_message.dart';
import '../crypto/signal_protocol.dart';

/// Represents an encrypted file with metadata
class EncryptedFile {
  /// The encrypted file data
  final Uint8List encryptedData;

  /// Unique nonce used for encryption
  final Uint8List nonce;

  /// Authentication tag for integrity verification
  final Uint8List tag;

  /// Original file name (encrypted separately)
  final String? encryptedFileName;

  /// Original file extension
  final String? fileExtension;

  /// Original file size before encryption
  final int originalSize;

  const EncryptedFile({
    required this.encryptedData,
    required this.nonce,
    required this.tag,
    this.encryptedFileName,
    this.fileExtension,
    required this.originalSize,
  });

  /// Total size of encrypted data
  int get encryptedSize => encryptedData.length;
}

/// Service for encrypting and decrypting media files
/// Requirements: 6.2 - Encrypt media files before upload
class MediaEncryptionService {
  final SignalProtocol _signalProtocol;

  MediaEncryptionService({
    required SignalProtocol signalProtocol,
  }) : _signalProtocol = signalProtocol;

  /// Encrypt a media file before upload
  /// Requirements: 6.2 - Encrypt the file before upload
  Future<EncryptedFile> encryptMedia(File file, Uint8List sessionKey) async {
    final bytes = await file.readAsBytes();
    return encryptMediaBytes(
      bytes,
      sessionKey,
      fileName: path.basename(file.path),
    );
  }

  /// Encrypt media bytes directly
  EncryptedFile encryptMediaBytes(
    Uint8List data,
    Uint8List sessionKey, {
    String? fileName,
  }) {
    final encrypted = _signalProtocol.encryptBytes(data, sessionKey);

    String? encryptedFileName;
    String? fileExtension;

    if (fileName != null) {
      fileExtension = path.extension(fileName);
      // Encrypt the filename for additional privacy
      final nameEncrypted = _signalProtocol.encrypt(fileName, sessionKey);
      encryptedFileName = _bytesToHex(nameEncrypted.ciphertext);
    }

    return EncryptedFile(
      encryptedData: encrypted.ciphertext,
      nonce: encrypted.nonce,
      tag: encrypted.tag,
      encryptedFileName: encryptedFileName,
      fileExtension: fileExtension,
      originalSize: data.length,
    );
  }


  /// Decrypt downloaded media
  Future<File> decryptMedia(
    EncryptedFile encrypted,
    Uint8List sessionKey,
    String outputPath,
  ) async {
    final decryptedBytes = decryptMediaBytes(encrypted, sessionKey);
    final file = File(outputPath);
    await file.writeAsBytes(decryptedBytes);
    return file;
  }

  /// Decrypt media bytes directly
  Uint8List decryptMediaBytes(EncryptedFile encrypted, Uint8List sessionKey) {
    final encryptedMessage = EncryptedMessage(
      ciphertext: encrypted.encryptedData,
      nonce: encrypted.nonce,
      tag: encrypted.tag,
    );

    return _signalProtocol.decryptBytes(encryptedMessage, sessionKey);
  }

  /// Encrypt a large file in chunks (for streaming)
  Stream<Uint8List> encryptMediaStream(
    Stream<List<int>> inputStream,
    Uint8List sessionKey,
  ) async* {
    await for (final chunk in inputStream) {
      final encrypted = _signalProtocol.encryptBytes(
        Uint8List.fromList(chunk),
        sessionKey,
      );
      // Yield: nonce length (1 byte) + nonce + tag + ciphertext
      final output = Uint8List(1 + encrypted.nonce.length + encrypted.tag.length + encrypted.ciphertext.length);
      output[0] = encrypted.nonce.length;
      output.setRange(1, 1 + encrypted.nonce.length, encrypted.nonce);
      output.setRange(
        1 + encrypted.nonce.length,
        1 + encrypted.nonce.length + encrypted.tag.length,
        encrypted.tag,
      );
      output.setRange(
        1 + encrypted.nonce.length + encrypted.tag.length,
        output.length,
        encrypted.ciphertext,
      );
      yield output;
    }
  }

  /// Generate a new random session key for media encryption
  Future<Uint8List> generateMediaKey() async {
    final keyPair = await _signalProtocol.generateKeyPair();
    // Use the first 32 bytes of the private key as the media key
    return keyPair.privateKey.sublist(0, 32);
  }

  /// Verify that encrypted data doesn't contain plaintext
  /// This is useful for testing/validation
  bool verifyNoPlaintextLeakage(Uint8List original, Uint8List encrypted) {
    if (original.length < 16 || encrypted.length < 16) {
      return true; // Too small to reliably check
    }

    // Check for any 16-byte sequence from original in encrypted
    for (int i = 0; i <= original.length - 16; i++) {
      final sequence = original.sublist(i, i + 16);
      if (_containsSequence(encrypted, sequence)) {
        return false; // Found plaintext in encrypted data
      }
    }
    return true;
  }

  bool _containsSequence(Uint8List data, Uint8List sequence) {
    if (sequence.length > data.length) return false;

    for (int i = 0; i <= data.length - sequence.length; i++) {
      bool match = true;
      for (int j = 0; j < sequence.length; j++) {
        if (data[i + j] != sequence[j]) {
          match = false;
          break;
        }
      }
      if (match) return true;
    }
    return false;
  }

  String _bytesToHex(Uint8List bytes) {
    return bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
  }
}
