import 'package:flutter_test/flutter_test.dart';
import 'package:mkr_flutter/data/crypto/signal_protocol.dart';
import 'package:mkr_flutter/domain/entities/key_pair.dart';

void main() {
  late SignalProtocol protocol;

  setUp(() {
    protocol = SignalProtocol();
  });

  group('SignalProtocol', () {
    group('Key Generation', () {
      test('generates valid X25519 key pair', () async {
        final keyPair = await protocol.generateKeyPair();

        expect(keyPair.publicKey.length, equals(32));
        expect(keyPair.privateKey.length, equals(32));
      });

      test('generates different key pairs each time', () async {
        final keyPair1 = await protocol.generateKeyPair();
        final keyPair2 = await protocol.generateKeyPair();

        expect(keyPair1.publicKey, isNot(equals(keyPair2.publicKey)));
        expect(keyPair1.privateKey, isNot(equals(keyPair2.privateKey)));
      });
    });

    group('Key Exchange', () {
      test('derives same shared secret from both sides', () async {
        final aliceKeys = await protocol.generateKeyPair();
        final bobKeys = await protocol.generateKeyPair();

        final aliceShared = await protocol.performKeyExchange(
          aliceKeys.privateKey,
          bobKeys.publicKey,
        );
        final bobShared = await protocol.performKeyExchange(
          bobKeys.privateKey,
          aliceKeys.publicKey,
        );

        expect(aliceShared, equals(bobShared));
      });
    });

    group('Encryption/Decryption', () {
      test('encrypts and decrypts message correctly', () async {
        final keyPair = await protocol.generateKeyPair();
        final sessionKey = protocol.deriveSessionKey(keyPair.privateKey);
        const plaintext = 'Hello, World!';

        final encrypted = protocol.encrypt(plaintext, sessionKey);
        final decrypted = protocol.decrypt(encrypted, sessionKey);

        expect(decrypted, equals(plaintext));
      });

      test('generates unique nonce for each encryption', () async {
        final keyPair = await protocol.generateKeyPair();
        final sessionKey = protocol.deriveSessionKey(keyPair.privateKey);
        const plaintext = 'Same message';

        final encrypted1 = protocol.encrypt(plaintext, sessionKey);
        final encrypted2 = protocol.encrypt(plaintext, sessionKey);

        expect(encrypted1.nonce, isNot(equals(encrypted2.nonce)));
        expect(encrypted1.ciphertext, isNot(equals(encrypted2.ciphertext)));
      });

      test('handles empty string', () async {
        final keyPair = await protocol.generateKeyPair();
        final sessionKey = protocol.deriveSessionKey(keyPair.privateKey);
        const plaintext = '';

        final encrypted = protocol.encrypt(plaintext, sessionKey);
        final decrypted = protocol.decrypt(encrypted, sessionKey);

        expect(decrypted, equals(plaintext));
      });

      test('handles unicode characters', () async {
        final keyPair = await protocol.generateKeyPair();
        final sessionKey = protocol.deriveSessionKey(keyPair.privateKey);
        const plaintext = 'Привет мир! 🔐 日本語';

        final encrypted = protocol.encrypt(plaintext, sessionKey);
        final decrypted = protocol.decrypt(encrypted, sessionKey);

        expect(decrypted, equals(plaintext));
      });
    });

    group('Key Serialization', () {
      test('serializes and deserializes key pair correctly', () async {
        final original = await protocol.generateKeyPair();

        final json = protocol.serializeKeys(original);
        final restored = protocol.deserializeKeys(json);

        expect(restored.publicKey, equals(original.publicKey));
        expect(restored.privateKey, equals(original.privateKey));
      });

      test('KeyPair toJson/fromJson round trip', () async {
        final original = await protocol.generateKeyPair();

        final json = original.toJson();
        final restored = KeyPair.fromJson(json);

        expect(restored, equals(original));
      });
    });
  });
}
