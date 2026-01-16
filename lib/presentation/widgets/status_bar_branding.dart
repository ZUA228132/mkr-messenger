import 'package:flutter/cupertino.dart';

/// Виджет брендинга для status bar area (под монобровь)
/// Показывает "MKR" в верхней части экрана для скриншотов
class StatusBarBranding extends StatelessWidget {
  final Widget child;
  final bool showBranding;
  
  const StatusBarBranding({
    super.key,
    required this.child,
    this.showBranding = true,
  });

  @override
  Widget build(BuildContext context) {
    if (!showBranding) return child;
    
    final brightness = MediaQuery.platformBrightnessOf(context);
    final isDark = brightness == Brightness.dark;
    final topPadding = MediaQuery.of(context).padding.top;
    
    return Stack(
      children: [
        child,
        // Брендинг под монобровь
        Positioned(
          top: 0,
          left: 0,
          right: 0,
          child: Container(
            height: topPadding + 44,
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [
                  (isDark ? CupertinoColors.black : CupertinoColors.white).withAlpha(242),
                  (isDark ? CupertinoColors.black : CupertinoColors.white).withAlpha(0),
                ],
              ),
            ),
            child: SafeArea(
              bottom: false,
              child: Center(
                child: Text(
                  'MKR',
                  style: TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                    letterSpacing: 3,
                    color: isDark 
                        ? CupertinoColors.white.withAlpha(153)
                        : CupertinoColors.black.withAlpha(153),
                  ),
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}

/// Простой брендинг текст для навбара
class NavBarBranding extends StatelessWidget {
  const NavBarBranding({super.key});

  @override
  Widget build(BuildContext context) {
    return const Text(
      'MKR',
      style: TextStyle(
        fontSize: 17,
        fontWeight: FontWeight.w700,
        letterSpacing: 2,
      ),
    );
  }
}
