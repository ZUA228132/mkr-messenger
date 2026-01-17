import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';

import '../../data/services/secure_wipe_service.dart';

/// Screen for the Panic Button feature - matching Android UI
/// Requirements: 4.1 - Hold button 3 seconds to trigger, confirmation dialog
class PanicButtonScreen extends StatefulWidget {
  final SecureWipeService wipeService;
  final VoidCallback? onWipeComplete;

  const PanicButtonScreen({
    super.key,
    required this.wipeService,
    this.onWipeComplete,
  });

  @override
  State<PanicButtonScreen> createState() => _PanicButtonScreenState();
}

class _PanicButtonScreenState extends State<PanicButtonScreen>
    with SingleTickerProviderStateMixin {
  static const Duration _holdDuration = Duration(seconds: 3);

  bool _isHolding = false;
  bool _isWiping = false;
  bool _wipeComplete = false;
  double _holdProgress = 0.0;
  Timer? _holdTimer;
  late AnimationController _pulseController;

  @override
  void initState() {
    super.initState();
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 800),
    )..repeat(reverse: true);
  }

  @override
  void dispose() {
    _holdTimer?.cancel();
    _pulseController.dispose();
    super.dispose();
  }

  void _startHold() {
    if (_isWiping) return;

    setState(() {
      _isHolding = true;
      _holdProgress = 0.0;
    });

    HapticFeedback.mediumImpact();

    const updateInterval = Duration(milliseconds: 16); // ~60fps
    final totalUpdates = _holdDuration.inMilliseconds ~/ updateInterval.inMilliseconds;
    int currentUpdate = 0;
    int lastVibrationPercent = 0;

    _holdTimer = Timer.periodic(updateInterval, (timer) {
      currentUpdate++;
      final newProgress = currentUpdate / totalUpdates;

      // Вибрация на каждые 25%
      final progressPercent = (newProgress * 100).toInt();
      if (progressPercent >= 25 && lastVibrationPercent < 25) {
        HapticFeedback.mediumImpact();
      } else if (progressPercent >= 50 && lastVibrationPercent < 50) {
        HapticFeedback.mediumImpact();
      } else if (progressPercent >= 75 && lastVibrationPercent < 75) {
        HapticFeedback.mediumImpact();
      }
      lastVibrationPercent = progressPercent;

      setState(() {
        _holdProgress = newProgress;
      });

      if (currentUpdate >= totalUpdates) {
        timer.cancel();
        _onHoldComplete();
      }
    });
  }

  void _cancelHold() {
    _holdTimer?.cancel();
    setState(() {
      _isHolding = false;
      _holdProgress = 0.0;
    });
  }

  void _onHoldComplete() {
    HapticFeedback.heavyImpact();
    setState(() => _isHolding = false);
    _showConfirmationDialog();
  }

  Future<void> _showConfirmationDialog() async {
    final confirmed = await showCupertinoDialog<bool>(
      context: context,
      builder: (context) => CupertinoAlertDialog(
        title: const Row(
          children: [
            Icon(CupertinoIcons.exclamationmark_triangle_fill, color: CupertinoColors.systemRed, size: 28),
            SizedBox(width: 8),
            Text('Подтвердите удаление'),
          ],
        ),
        content: const Text(
          'ВСЕ ДАННЫЕ будут безвозвратно удалены.\n\nЭто действие НЕОБРАТИМО!',
          textAlign: TextAlign.center,
        ),
        actions: [
          CupertinoDialogAction(
            isDestructiveAction: true,
            onPressed: () => Navigator.of(context).pop(true),
            child: const Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(CupertinoIcons.delete),
                SizedBox(width: 8),
                Text('УДАЛИТЬ ВСЁ'),
              ],
            ),
          ),
          CupertinoDialogAction(
            isDefaultAction: true,
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Отмена'),
          ),
        ],
      ),
    );

    if (confirmed == true) {
      await _executeWipe();
    }
  }

  Future<void> _executeWipe() async {
    setState(() => _isWiping = true);

    // Simulate wipe process
    await Future.delayed(const Duration(seconds: 1));

    final result = await widget.wipeService.executeWipe();

    if (!mounted) return;

    setState(() {
      _wipeComplete = true;
    });

    await Future.delayed(const Duration(milliseconds: 1500));

    if (!mounted) return;

    setState(() => _isWiping = false);

    if (result.isComplete) {
      // Show success and trigger callback
      await showCupertinoDialog(
        context: context,
        builder: (context) => CupertinoAlertDialog(
          content: const Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(CupertinoIcons.checkmark_circle_fill, color: CupertinoColors.systemGreen, size: 48),
              SizedBox(width: 16),
              Expanded(
                child: Text(
                  '✓ Все данные удалены',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600),
                ),
              ),
            ],
          ),
          actions: [
            CupertinoDialogAction(
              onPressed: () {
                Navigator.of(context).pop();
                widget.onWipeComplete?.call();
              },
              child: const Text('OK'),
            ),
          ],
        ),
      );
    } else {
      // Show error
      await showCupertinoDialog(
        context: context,
        builder: (context) => CupertinoAlertDialog(
          title: const Text('Ошибка'),
          content: Text(
            'Не удалось полностью удалить данные.\n'
            '${result.error ?? "Попробуйте ещё раз."}',
          ),
          actions: [
            CupertinoDialogAction(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('OK'),
            ),
          ],
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      backgroundColor: const Color(0xFF1A1A2E),
      navigationBar: CupertinoNavigationBar(
        backgroundColor: const Color(0xFF1A1A2E),
        middle: const Text(
          'Экстренное удаление',
          style: TextStyle(color: CupertinoColors.white),
        ),
        leading: CupertinoButton(
          padding: EdgeInsets.zero,
          onPressed: () => Navigator.of(context).pop(),
          child: const Icon(
            CupertinoIcons.back,
            color: CupertinoColors.white,
          ),
        ),
      ),
      child: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            children: [
              // Верхняя часть - предупреждение
              const Column(
                children: [
                  Icon(
                    CupertinoIcons.exclamationmark_triangle_fill,
                    size: 56,
                    color: Color(0xFFFF5252),
                  ),
                  SizedBox(height: 16),
                  Text(
                    'PANIC BUTTON',
                    style: TextStyle(
                      fontSize: 28,
                      fontWeight: FontWeight.bold,
                      color: CupertinoColors.white,
                    ),
                  ),
                  SizedBox(height: 16),
                  Padding(
                    padding: EdgeInsets.symmetric(horizontal: 32),
                    child: Text(
                      'Удерживайте кнопку 3 секунды\nдля полного удаления всех данных',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 14,
                        color: Color(0xB3FFFFFF), // white with 0.7 alpha
                      ),
                    ),
                  ),
                ],
              ),

              const Spacer(),

              // Центр - кнопка
              _buildPanicButton(),

              const Spacer(),

              // Нижняя часть - статус и информация
              Column(
                children: [
                  if (_isWiping) ...[
                    Text(
                      _wipeComplete ? '✓ Все данные удалены' : 'Удаление данных...',
                      style: TextStyle(
                        color: _wipeComplete ? const Color(0xFF4CAF50) : const Color(0xFFFF9800),
                        fontWeight: FontWeight.w500,
                        fontSize: 16,
                      ),
                    ),
                  ] else ...[
                    Text(
                      _isHolding
                          ? 'Удерживайте... ${(_holdProgress * 3).toInt() + 1}с'
                          : 'Нажмите и удерживайте',
                      style: const TextStyle(
                        color: Color(0x99FFFFFF), // white with 0.6 alpha
                        fontSize: 14,
                      ),
                    ),
                  ],

                  const SizedBox(height: 24),

                  // Что будет удалено
                  _buildWipeInfoCard(),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildPanicButton() {
    return SizedBox(
      width: 200,
      height: 200,
      child: Stack(
        alignment: Alignment.center,
        children: [
          // Прогресс круг
          if (_holdProgress > 0 && !_isWiping)
            Container(
              width: 200,
              height: 200,
              padding: const EdgeInsets.all(8),
              child: CupertinoActivityIndicator.partiallyRevealed(
                radius: 100,
                progress: _holdProgress,
              ),
            ),

          // Основная кнопка
          AnimatedBuilder(
            animation: _pulseController,
            builder: (context, child) {
              final scale = _isHolding
                  ? 0.92 + (_holdProgress * 0.08)
                  : _wipeComplete
                      ? 1.0
                      : 1.0 + (_pulseController.value * 0.08);

              return Transform.scale(
                scale: scale,
                child: GestureDetector(
                  onLongPressStart: (_) => _startHold(),
                  onLongPressEnd: (_) => _cancelHold(),
                  onLongPressCancel: _cancelHold,
                  child: Container(
                    width: 160,
                    height: 160,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      gradient: _getButtonGradient(),
                      boxShadow: [
                        BoxShadow(
                          color: _getButtonColor().withValues(alpha: 0.5),
                          blurRadius: _isHolding ? 20 + (_holdProgress * 20) : 15,
                          spreadRadius: _isHolding ? 5 + (_holdProgress * 10) : 2,
                        ),
                      ],
                    ),
                    child: Center(
                      child: _isWiping
                          ? _wipeComplete
                              ? const Icon(
                                  CupertinoIcons.checkmark,
                                  size: 64,
                                  color: CupertinoColors.white,
                                )
                              : const CupertinoActivityIndicator(
                                  color: CupertinoColors.white,
                                  radius: 24,
                                )
                          : const Column(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(
                                  CupertinoIcons.delete,
                                  size: 56,
                                  color: CupertinoColors.white,
                                ),
                                SizedBox(height: 4),
                                Text(
                                  'УДАЛИТЬ',
                                  style: TextStyle(
                                    color: CupertinoColors.white,
                                    fontWeight: FontWeight.bold,
                                    fontSize: 16,
                                  ),
                                ),
                              ],
                            ),
                    ),
                  ),
                ),
              );
            },
          ),
        ],
      ),
    );
  }

  LinearGradient _getButtonGradient() {
    if (_isWiping) {
      if (_wipeComplete) {
        return const LinearGradient(
          colors: [Color(0xFF66BB6A), Color(0xFF4CAF50)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        );
      }
      return const LinearGradient(
        colors: [Color(0xFFFF9800), Color(0xFFFF5722)],
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
      );
    }
    return const LinearGradient(
      colors: [Color(0xFFFF7043), Color(0xFFFF5252), Color(0xFFD32F2F)],
      begin: Alignment.topLeft,
      end: Alignment.bottomRight,
    );
  }

  Color _getButtonColor() {
    if (_isWiping) {
      if (_wipeComplete) return const Color(0xFF4CAF50);
      return const Color(0xFFFF5722);
    }
    return const Color(0xFFFF5252);
  }

  Widget _buildWipeInfoCard() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFF2D2D44),
        borderRadius: BorderRadius.circular(12),
      ),
      child: const Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Будут удалены:',
            style: TextStyle(
              color: Color(0xFFFF5252),
              fontWeight: FontWeight.w600,
              fontSize: 14,
            ),
          ),
          SizedBox(height: 8),
          Text(
            '• Все сообщения и чаты',
            style: TextStyle(
              color: Color(0xB3FFFFFF), // white with 0.7 alpha
              fontSize: 13,
            ),
          ),
          Text(
            '• Ключи шифрования',
            style: TextStyle(
              color: Color(0xB3FFFFFF),
              fontSize: 13,
            ),
          ),
          Text(
            '• Медиафайлы',
            style: TextStyle(
              color: Color(0xB3FFFFFF),
              fontSize: 13,
            ),
          ),
          Text(
            '• Настройки и профиль',
            style: TextStyle(
              color: Color(0xB3FFFFFF),
              fontSize: 13,
            ),
          ),
        ],
      ),
    );
  }
}
