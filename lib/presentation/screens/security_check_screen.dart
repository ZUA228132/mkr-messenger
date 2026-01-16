import 'package:flutter/cupertino.dart';

import '../../data/services/security_checker.dart';

/// Screen for displaying device security status and risk level
/// Requirements: 7.4 - Display security status and risk level
class SecurityCheckScreen extends StatefulWidget {
  final SecurityChecker securityChecker;

  const SecurityCheckScreen({
    super.key,
    required this.securityChecker,
  });

  @override
  State<SecurityCheckScreen> createState() => _SecurityCheckScreenState();
}

class _SecurityCheckScreenState extends State<SecurityCheckScreen> {
  SecurityReport? _report;
  bool _isChecking = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _runSecurityCheck();
  }

  Future<void> _runSecurityCheck() async {
    setState(() {
      _isChecking = true;
      _error = null;
    });

    try {
      final report = await widget.securityChecker.runChecks();
      if (mounted) {
        setState(() {
          _report = report;
          _isChecking = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e.toString();
          _isChecking = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      navigationBar: CupertinoNavigationBar(
        middle: const Text('Безопасность'),
        trailing: CupertinoButton(
          padding: EdgeInsets.zero,
          onPressed: _isChecking ? null : _runSecurityCheck,
          child: const Icon(CupertinoIcons.refresh),
        ),
      ),
      child: SafeArea(
        child: _buildContent(),
      ),
    );
  }

  Widget _buildContent() {
    if (_isChecking) {
      return const Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            CupertinoActivityIndicator(radius: 20),
            SizedBox(height: 16),
            Text(
              'Проверка безопасности...',
              style: TextStyle(
                fontSize: 16,
                color: CupertinoColors.systemGrey,
              ),
            ),
          ],
        ),
      );
    }

    if (_error != null) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(
              CupertinoIcons.exclamationmark_circle,
              size: 60,
              color: CupertinoColors.systemRed,
            ),
            const SizedBox(height: 16),
            const Text(
              'Ошибка проверки',
              style: TextStyle(
                fontSize: 20,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 8),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 32),
              child: Text(
                _error!,
                textAlign: TextAlign.center,
                style: const TextStyle(
                  color: CupertinoColors.systemGrey,
                ),
              ),
            ),
            const SizedBox(height: 24),
            CupertinoButton.filled(
              onPressed: _runSecurityCheck,
              child: const Text('Повторить'),
            ),
          ],
        ),
      );
    }

    if (_report == null) {
      return const Center(
        child: Text('Нет данных'),
      );
    }

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _buildRiskLevelCard(),
          const SizedBox(height: 24),
          _buildSecurityChecksSection(),
          const SizedBox(height: 24),
          _buildRecommendationsSection(),
        ],
      ),
    );
  }

  Widget _buildRiskLevelCard() {
    final riskLevel = _report!.riskLevel;
    final color = _getRiskLevelColor(riskLevel);
    final icon = _getRiskLevelIcon(riskLevel);

    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: color.withOpacity(0.3)),
      ),
      child: Column(
        children: [
          Icon(icon, size: 60, color: color),
          const SizedBox(height: 16),
          Text(
            _getRiskLevelTitle(riskLevel),
            style: TextStyle(
              fontSize: 24,
              fontWeight: FontWeight.bold,
              color: color,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            _getRiskLevelDescription(riskLevel),
            textAlign: TextAlign.center,
            style: const TextStyle(
              fontSize: 14,
              color: CupertinoColors.systemGrey,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSecurityChecksSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Результаты проверки',
          style: TextStyle(
            fontSize: 18,
            fontWeight: FontWeight.bold,
          ),
        ),
        const SizedBox(height: 12),
        _buildCheckItem(
          title: 'Jailbreak / Root',
          subtitle: 'Проверка модификации системы',
          isIssue: _report!.isDeviceCompromised,
          icon: CupertinoIcons.lock_shield,
        ),
        _buildCheckItem(
          title: 'Отладчик',
          subtitle: 'Проверка подключения отладчика',
          isIssue: _report!.isDebuggerAttached,
          icon: CupertinoIcons.ant,
        ),
        _buildCheckItem(
          title: 'Эмулятор',
          subtitle: 'Проверка виртуального устройства',
          isIssue: _report!.isEmulator,
          icon: CupertinoIcons.device_phone_portrait,
        ),
      ],
    );
  }

  Widget _buildCheckItem({
    required String title,
    required String subtitle,
    required bool isIssue,
    required IconData icon,
  }) {
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: CupertinoColors.systemBackground,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: isIssue
              ? CupertinoColors.systemRed.withOpacity(0.3)
              : CupertinoColors.systemGreen.withOpacity(0.3),
        ),
      ),
      child: Row(
        children: [
          Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              color: isIssue
                  ? CupertinoColors.systemRed.withOpacity(0.1)
                  : CupertinoColors.systemGreen.withOpacity(0.1),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Icon(
              icon,
              color: isIssue
                  ? CupertinoColors.systemRed
                  : CupertinoColors.systemGreen,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                Text(
                  subtitle,
                  style: const TextStyle(
                    fontSize: 13,
                    color: CupertinoColors.systemGrey,
                  ),
                ),
              ],
            ),
          ),
          Icon(
            isIssue
                ? CupertinoIcons.exclamationmark_circle_fill
                : CupertinoIcons.checkmark_circle_fill,
            color: isIssue
                ? CupertinoColors.systemRed
                : CupertinoColors.systemGreen,
          ),
        ],
      ),
    );
  }

  Widget _buildRecommendationsSection() {
    if (!_report!.hasIssues) {
      return const SizedBox.shrink();
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Рекомендации',
          style: TextStyle(
            fontSize: 18,
            fontWeight: FontWeight.bold,
          ),
        ),
        const SizedBox(height: 12),
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: CupertinoColors.systemYellow.withOpacity(0.1),
            borderRadius: BorderRadius.circular(12),
            border: Border.all(
              color: CupertinoColors.systemYellow.withOpacity(0.3),
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (_report!.isDeviceCompromised) ...[
                _buildRecommendation(
                  'Устройство модифицировано (jailbreak/root). '
                  'Это серьёзная угроза безопасности. '
                  'Рекомендуется использовать немодифицированное устройство.',
                ),
                const SizedBox(height: 8),
              ],
              if (_report!.isDebuggerAttached) ...[
                _buildRecommendation(
                  'Обнаружен отладчик. Это может указывать на попытку '
                  'анализа приложения. Закройте средства разработки.',
                ),
                const SizedBox(height: 8),
              ],
              if (_report!.isEmulator) ...[
                _buildRecommendation(
                  'Приложение запущено на эмуляторе. '
                  'Для максимальной безопасности используйте реальное устройство.',
                ),
              ],
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildRecommendation(String text) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Icon(
          CupertinoIcons.lightbulb,
          size: 18,
          color: CupertinoColors.systemYellow,
        ),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            text,
            style: const TextStyle(fontSize: 14),
          ),
        ),
      ],
    );
  }

  Color _getRiskLevelColor(RiskLevel level) {
    switch (level) {
      case RiskLevel.safe:
        return CupertinoColors.systemGreen;
      case RiskLevel.low:
        return CupertinoColors.systemBlue;
      case RiskLevel.medium:
        return CupertinoColors.systemYellow;
      case RiskLevel.high:
        return CupertinoColors.systemOrange;
      case RiskLevel.critical:
        return CupertinoColors.systemRed;
    }
  }

  IconData _getRiskLevelIcon(RiskLevel level) {
    switch (level) {
      case RiskLevel.safe:
        return CupertinoIcons.shield_lefthalf_fill;
      case RiskLevel.low:
        return CupertinoIcons.shield;
      case RiskLevel.medium:
        return CupertinoIcons.exclamationmark_shield;
      case RiskLevel.high:
        return CupertinoIcons.exclamationmark_triangle;
      case RiskLevel.critical:
        return CupertinoIcons.xmark_shield;
    }
  }

  String _getRiskLevelTitle(RiskLevel level) {
    switch (level) {
      case RiskLevel.safe:
        return 'Безопасно';
      case RiskLevel.low:
        return 'Низкий риск';
      case RiskLevel.medium:
        return 'Средний риск';
      case RiskLevel.high:
        return 'Высокий риск';
      case RiskLevel.critical:
        return 'Критический риск';
    }
  }

  String _getRiskLevelDescription(RiskLevel level) {
    switch (level) {
      case RiskLevel.safe:
        return 'Устройство прошло все проверки безопасности. '
            'Ваши данные защищены.';
      case RiskLevel.low:
        return 'Обнаружены незначительные проблемы. '
            'Рекомендуется обратить внимание.';
      case RiskLevel.medium:
        return 'Обнаружены проблемы безопасности. '
            'Рекомендуется принять меры.';
      case RiskLevel.high:
        return 'Обнаружены серьёзные проблемы безопасности. '
            'Настоятельно рекомендуется принять меры.';
      case RiskLevel.critical:
        return 'Устройство скомпрометировано! '
            'Использование приложения небезопасно.';
    }
  }
}
