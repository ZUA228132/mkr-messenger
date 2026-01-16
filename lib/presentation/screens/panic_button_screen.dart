import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';

import '../../data/services/secure_wipe_service.dart';

/// Screen for the Panic Button feature
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
  double _holdProgress = 0.0;
  Timer? _holdTimer;
  late AnimationController _pulseController;

  @override
  void initState() {
    super.initState();
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1000),
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

    // Haptic feedback on start
    HapticFeedback.mediumImpact();

    const updateInterval = Duration(milliseconds: 50);
    final totalUpdates = _holdDuration.inMilliseconds ~/ updateInterval.inMilliseconds;
    int currentUpdate = 0;

    _holdTimer = Timer.periodic(updateInterval, (timer) {
      currentUpdate++;
      setState(() {
        _holdProgress = currentUpdate / totalUpdates;
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
    // Strong haptic feedback on complete
    HapticFeedback.heavyImpact();

    setState(() {
      _isHolding = false;
    });

    _showConfirmationDialog();
  }

  Future<void> _showConfirmationDialog() async {
    final confirmed = await showCupertinoDialog<bool>(
      context: context,
      builder: (context) => CupertinoAlertDialog(
        title: const Text('Подтверждение'),
        content: const Text(
          'Вы уверены, что хотите удалить ВСЕ данные?\n\n'
          'Это действие необратимо. Все сообщения, ключи шифрования '
          'и медиафайлы будут безвозвратно удалены.',
        ),
        actions: [
          CupertinoDialogAction(
            isDestructiveAction: true,
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('Удалить всё'),
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
    setState(() {
      _isWiping = true;
    });

    final result = await widget.wipeService.executeWipe();

    if (!mounted) return;

    setState(() {
      _isWiping = false;
    });

    if (result.isComplete) {
      // Show success and trigger callback
      await showCupertinoDialog(
        context: context,
        builder: (context) => CupertinoAlertDialog(
          title: const Text('Готово'),
          content: const Text('Все данные успешно удалены.'),
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
      navigationBar: const CupertinoNavigationBar(
        middle: Text('Экстренное удаление'),
      ),
      child: SafeArea(
        child: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(
                CupertinoIcons.exclamationmark_triangle_fill,
                size: 60,
                color: CupertinoColors.systemRed,
              ),
              const SizedBox(height: 24),
              const Text(
                'Panic Button',
                style: TextStyle(
                  fontSize: 28,
                  fontWeight: FontWeight.bold,
                  color: CupertinoColors.systemRed,
                ),
              ),
              const SizedBox(height: 12),
              const Padding(
                padding: EdgeInsets.symmetric(horizontal: 32),
                child: Text(
                  'Удерживайте кнопку 3 секунды для экстренного удаления всех данных',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 16,
                    color: CupertinoColors.systemGrey,
                  ),
                ),
              ),
              const SizedBox(height: 48),
              _buildPanicButton(),
              const SizedBox(height: 24),
              if (_isHolding) _buildProgressIndicator(),
              if (_isWiping) _buildWipingIndicator(),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildPanicButton() {
    return GestureDetector(
      onLongPressStart: (_) => _startHold(),
      onLongPressEnd: (_) => _cancelHold(),
      onLongPressCancel: _cancelHold,
      child: AnimatedBuilder(
        animation: _pulseController,
        builder: (context, child) {
          final scale = _isHolding
              ? 1.0 + (_holdProgress * 0.1)
              : 1.0 + (_pulseController.value * 0.05);

          return Transform.scale(
            scale: scale,
            child: Container(
              width: 150,
              height: 150,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: _isWiping
                    ? CupertinoColors.systemGrey
                    : _isHolding
                        ? CupertinoColors.systemRed.withOpacity(
                            0.7 + (_holdProgress * 0.3),
                          )
                        : CupertinoColors.systemRed,
                boxShadow: [
                  BoxShadow(
                    color: CupertinoColors.systemRed.withOpacity(
                      _isHolding ? 0.5 + (_holdProgress * 0.3) : 0.3,
                    ),
                    blurRadius: _isHolding ? 20 + (_holdProgress * 20) : 15,
                    spreadRadius: _isHolding ? 5 + (_holdProgress * 10) : 2,
                  ),
                ],
              ),
              child: Center(
                child: _isWiping
                    ? const CupertinoActivityIndicator(
                        color: CupertinoColors.white,
                      )
                    : const Icon(
                        CupertinoIcons.trash_fill,
                        size: 60,
                        color: CupertinoColors.white,
                      ),
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _buildProgressIndicator() {
    return Column(
      children: [
        SizedBox(
          width: 200,
          height: 8,
          child: ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: Stack(
              children: [
                Container(
                  decoration: BoxDecoration(
                    color: CupertinoColors.systemGrey5,
                    borderRadius: BorderRadius.circular(4),
                  ),
                ),
                FractionallySizedBox(
                  widthFactor: _holdProgress,
                  child: Container(
                    decoration: BoxDecoration(
                      color: CupertinoColors.systemRed,
                      borderRadius: BorderRadius.circular(4),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 8),
        Text(
          '${(_holdProgress * 3).toStringAsFixed(1)} / 3.0 сек',
          style: const TextStyle(
            fontSize: 14,
            color: CupertinoColors.systemGrey,
          ),
        ),
      ],
    );
  }

  Widget _buildWipingIndicator() {
    return const Column(
      children: [
        CupertinoActivityIndicator(radius: 15),
        SizedBox(height: 12),
        Text(
          'Удаление данных...',
          style: TextStyle(
            fontSize: 16,
            color: CupertinoColors.systemGrey,
          ),
        ),
      ],
    );
  }
}
