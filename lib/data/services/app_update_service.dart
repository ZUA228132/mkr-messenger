import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';
import 'package:install_plugin/install_plugin.dart';
import 'package:package_info_plus/package_info_plus.dart';

/// App update information - соответствует структуре version.json
class AppUpdateInfo {
  final int versionCode;
  final String versionName;
  final int minSupportedVersionCode;
  final bool isForceUpdate;
  final String updateUrl;
  final Map<String, String> changelog;
  final int apkSize;
  final String md5Checksum;
  final String releaseDate;
  final Map<String, AbiInfo>? abis;

  AppUpdateInfo({
    required this.versionCode,
    required this.versionName,
    required this.minSupportedVersionCode,
    required this.isForceUpdate,
    required this.updateUrl,
    required this.changelog,
    required this.apkSize,
    required this.md5Checksum,
    required this.releaseDate,
    this.abis,
  });

  factory AppUpdateInfo.fromJson(Map<String, dynamic> json) {
    return AppUpdateInfo(
      versionCode: json['versionCode'] as int,
      versionName: json['versionName'] as String,
      minSupportedVersionCode: json['minSupportedVersionCode'] as int? ?? 1,
      isForceUpdate: json['isForceUpdate'] as bool? ?? false,
      updateUrl: json['updateUrl'] as String,
      changelog: Map<String, String>.from(json['changelog'] as Map),
      apkSize: json['apkSize'] as int? ?? 0,
      md5Checksum: json['md5Checksum'] as String? ?? '',
      releaseDate: json['releaseDate'] as String? ?? '',
      abis: (json['abis'] as Map<String, dynamic>?)?.map(
        (key, value) => MapEntry(key, AbiInfo.fromJson(value as Map<String, dynamic>)),
      ),
    );
  }

  /// Compare version codes
  bool isNewerThan(int currentVersionCode) {
    return versionCode > currentVersionCode;
  }

  /// Get changelog for specified language
  String getChangelog(String language) {
    return changelog[language] ?? changelog['en'] ?? 'No changelog available';
  }
}

/// Information about APK for specific ABI
class AbiInfo {
  final String url;
  final int size;
  final String md5;

  AbiInfo({
    required this.url,
    required this.size,
    required this.md5,
  });

  factory AbiInfo.fromJson(Map<String, dynamic> json) {
    return AbiInfo(
      url: json['url'] as String,
      size: json['size'] as int,
      md5: json['md5'] as String,
    );
  }
}

/// Service for checking and installing app updates
class AppUpdateService {
  // TODO: Заменить на реальный URL вашего сервера с version.json
  static const String defaultCheckUrl = 'https://your-server.com/version.json';

  String _checkUrl;
  int? _cachedVersionCode;

  AppUpdateService({String? checkUrl}) : _checkUrl = checkUrl ?? defaultCheckUrl;

  /// Set custom check URL
  void setCheckUrl(String url) {
    _checkUrl = url;
  }

  /// Get current version code from package info
  Future<int> _getCurrentVersionCode() async {
    if (_cachedVersionCode != null) return _cachedVersionCode!;

    try {
      final packageInfo = await PackageInfo.fromPlatform();
      // versionCode может быть строкой или числом в зависимости от версии
      _cachedVersionCode = int.tryParse(packageInfo.buildNumber) ?? 1;
      return _cachedVersionCode!;
    } catch (e) {
      developer.log('Error getting package info: $e', name: 'AppUpdateService');
      return 1;
    }
  }

  /// Get current version name
  Future<String> getCurrentVersionName() async {
    try {
      final packageInfo = await PackageInfo.fromPlatform();
      return packageInfo.version;
    } catch (e) {
      developer.log('Error getting package info: $e', name: 'AppUpdateService');
      return 'Unknown';
    }
  }

  /// Get formatted version string
  Future<String> getCurrentVersion() async {
    try {
      final packageInfo = await PackageInfo.fromPlatform();
      return '${packageInfo.version} (build ${packageInfo.buildNumber})';
    } catch (e) {
      return 'Unknown';
    }
  }

  /// Check for updates
  Future<Result<AppUpdateInfo>> checkForUpdates() async {
    try {
      final response = await http.get(
        Uri.parse(_checkUrl),
        headers: {'Accept': 'application/json'},
      ).timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        final json = jsonDecode(response.body) as Map<String, dynamic>;
        final updateInfo = AppUpdateInfo.fromJson(json);
        final currentVersion = await _getCurrentVersionCode();

        if (updateInfo.isNewerThan(currentVersion)) {
          developer.log(
            'Update available: ${updateInfo.versionName} (current: $currentVersion)',
            name: 'AppUpdateService',
          );
          return Result.success(updateInfo);
        } else {
          return Result.failure(
            ApiError(message: 'No updates available'),
          );
        }
      } else {
        return Result.failure(
          ApiError(
            message: 'Failed to check for updates: ${response.statusCode}',
          ),
        );
      }
    } catch (e) {
      developer.log('Error checking for updates: $e', name: 'AppUpdateService');
      return Result.failure(ApiError(message: 'Failed to check for updates: $e'));
    }
  }

  /// Download and install update
  Future<Result<void>> downloadAndInstallUpdate(
    AppUpdateInfo updateInfo, {
    Function(int received, int total)? onProgress,
    Function(String)? onError,
  }) async {
    try {
      // Get temporary directory
      final dir = await getTemporaryDirectory();
      final file = File('${dir.path}/update_${updateInfo.versionCode}.apk');

      developer.log(
        'Starting download: ${updateInfo.updateUrl}',
        name: 'AppUpdateService',
      );

      // Download file with progress
      final request = http.Request('GET', Uri.parse(updateInfo.updateUrl));
      final response = await http.Client().send(request);

      final contentLength = response.contentLength ?? updateInfo.apkSize;
      int received = 0;

      final sink = file.openWrite();

      await response.stream.listen(
        (chunk) {
          sink.add(chunk);
          received += chunk.length;
          onProgress?.call(received, contentLength);
        },
        onDone: () async {
          await sink.close();
          developer.log('Download complete: ${file.path}', name: 'AppUpdateService');

          // Install the APK
          try {
            await InstallPlugin.installApk(
              file.path,
              'com.pioneer.messenger', // Убедитесь, что это правильный package name
            );
          } catch (e) {
            developer.log('Install error: $e', name: 'AppUpdateService');
            onError?.call('Failed to install APK: $e');
          }
        },
        onError: (e) {
          developer.log('Download error: $e', name: 'AppUpdateService');
          sink.close();
          onError?.call('Download failed: $e');
        },
        cancelOnError: true,
      ).asFuture();

      return Result.success(null);
    } catch (e) {
      developer.log('Error downloading update: $e', name: 'AppUpdateService');
      onError?.call('Download error: $e');
      return Result.failure(ApiError(message: 'Failed to download update: $e'));
    }
  }

  /// Format file size for display
  String formatFileSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    if (bytes < 1024 * 1024 * 1024) {
      return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    }
    return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(1)} GB';
  }

  /// Check if update is force required
  bool isForceUpdateRequired(AppUpdateInfo updateInfo, int currentVersionCode) {
    return updateInfo.isForceUpdate ||
        currentVersionCode < updateInfo.minSupportedVersionCode;
  }
}

/// Simple Result type for error handling
class Result<T> {
  final T? _value;
  final ApiError? _error;

  Result.success(this._value) : _error = null;
  Result.failure(this._error) : _value = null;

  bool get isSuccess => _error == null;
  bool get isFailure => _error != null;

  T get value => _value!;
  ApiError get error => _error!;

  void fold({
    required Function(T) onSuccess,
    required Function(ApiError) onFailure,
  }) {
    if (isSuccess) {
      onSuccess(value);
    } else {
      onFailure(error);
    }
  }
}

class ApiError {
  final String message;
  ApiError({required this.message});

  @override
  String toString() => message;
}
