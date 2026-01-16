import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';

import '../../data/services/stealth_mode_service.dart';

/// Fake calculator screen for stealth mode
/// 
/// Requirements: 5.2 - Display functional fake calculator interface
/// Requirements: 5.3 - Secret code input triggers unlock
class FakeCalculatorScreen extends StatefulWidget {
  final StealthModeService stealthService;
  final VoidCallback? onUnlock;
  final VoidCallback? onPanicTriggered;

  const FakeCalculatorScreen({
    super.key,
    required this.stealthService,
    this.onUnlock,
    this.onPanicTriggered,
  });

  @override
  State<FakeCalculatorScreen> createState() => _FakeCalculatorScreenState();
}

class _FakeCalculatorScreenState extends State<FakeCalculatorScreen> {
  String _display = '0';
  String _currentInput = '';
  String _operator = '';
  double _firstOperand = 0;
  bool _shouldResetDisplay = false;
  
  // Track input for secret code detection
  String _inputSequence = '';

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      backgroundColor: CupertinoColors.black,
      child: SafeArea(
        child: Column(
          children: [
            // Display area
            Expanded(
              flex: 2,
              child: _buildDisplay(),
            ),
            // Button grid
            Expanded(
              flex: 5,
              child: _buildButtonGrid(),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildDisplay() {
    return Container(
      padding: const EdgeInsets.all(24),
      alignment: Alignment.bottomRight,
      child: Text(
        _display,
        style: const TextStyle(
          fontSize: 72,
          fontWeight: FontWeight.w300,
          color: CupertinoColors.white,
        ),
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
    );
  }

  Widget _buildButtonGrid() {
    return Column(
      children: [
        _buildButtonRow(['C', '±', '%', '÷']),
        _buildButtonRow(['7', '8', '9', '×']),
        _buildButtonRow(['4', '5', '6', '-']),
        _buildButtonRow(['1', '2', '3', '+']),
        _buildButtonRow(['0', '.', '=']),
      ],
    );
  }

  Widget _buildButtonRow(List<String> buttons) {
    return Expanded(
      child: Row(
        children: buttons.map((button) {
          final isZero = button == '0';
          final isOperator = ['÷', '×', '-', '+', '='].contains(button);
          final isFunction = ['C', '±', '%'].contains(button);

          return Expanded(
            flex: isZero ? 2 : 1,
            child: _buildButton(
              button,
              isOperator: isOperator,
              isFunction: isFunction,
            ),
          );
        }).toList(),
      ),
    );
  }


  Widget _buildButton(
    String label, {
    bool isOperator = false,
    bool isFunction = false,
  }) {
    Color backgroundColor;
    Color textColor;

    if (isOperator) {
      backgroundColor = CupertinoColors.systemOrange;
      textColor = CupertinoColors.white;
    } else if (isFunction) {
      backgroundColor = CupertinoColors.systemGrey;
      textColor = CupertinoColors.black;
    } else {
      backgroundColor = CupertinoColors.darkBackgroundGray;
      textColor = CupertinoColors.white;
    }

    return Padding(
      padding: const EdgeInsets.all(4),
      child: CupertinoButton(
        padding: EdgeInsets.zero,
        onPressed: () => _onButtonPressed(label),
        child: Container(
          decoration: BoxDecoration(
            color: backgroundColor,
            borderRadius: BorderRadius.circular(40),
          ),
          alignment: Alignment.center,
          child: Text(
            label,
            style: TextStyle(
              fontSize: 32,
              fontWeight: FontWeight.w400,
              color: textColor,
            ),
          ),
        ),
      ),
    );
  }

  void _onButtonPressed(String button) {
    HapticFeedback.lightImpact();

    // Track input sequence for secret code
    _trackInputForSecretCode(button);

    switch (button) {
      case 'C':
        _clear();
        break;
      case '±':
        _toggleSign();
        break;
      case '%':
        _percentage();
        break;
      case '÷':
      case '×':
      case '-':
      case '+':
        _setOperator(button);
        break;
      case '=':
        _calculate();
        break;
      case '.':
        _addDecimal();
        break;
      default:
        _addDigit(button);
    }
  }

  void _trackInputForSecretCode(String button) {
    // Only track digits for secret code
    if (RegExp(r'[0-9]').hasMatch(button)) {
      _inputSequence += button;
      
      // Keep only last 20 characters to prevent memory issues
      if (_inputSequence.length > 20) {
        _inputSequence = _inputSequence.substring(_inputSequence.length - 20);
      }
      
      // Check for secret code after each digit
      _checkSecretCode();
    } else if (button == 'C') {
      // Clear also resets the input sequence
      _inputSequence = '';
    } else if (button == '=') {
      // Equals triggers a check and then clears
      _checkSecretCode();
      _inputSequence = '';
    }
  }

  Future<void> _checkSecretCode() async {
    if (_inputSequence.isEmpty) return;

    final result = await widget.stealthService.validateCode(_inputSequence);

    if (result.isSuccess) {
      // Secret code matched - unlock the messenger
      HapticFeedback.heavyImpact();
      widget.onUnlock?.call();
    } else if (result.isPanicTriggered) {
      // Panic was triggered due to too many failed attempts
      HapticFeedback.heavyImpact();
      widget.onPanicTriggered?.call();
    }
    // For failures, we don't show any indication - it just looks like normal calculator use
  }

  void _clear() {
    setState(() {
      _display = '0';
      _currentInput = '';
      _operator = '';
      _firstOperand = 0;
      _shouldResetDisplay = false;
    });
  }

  void _toggleSign() {
    if (_display == '0') return;

    setState(() {
      if (_display.startsWith('-')) {
        _display = _display.substring(1);
      } else {
        _display = '-$_display';
      }
      _currentInput = _display;
    });
  }

  void _percentage() {
    final value = double.tryParse(_display) ?? 0;
    setState(() {
      _display = _formatNumber(value / 100);
      _currentInput = _display;
    });
  }


  void _setOperator(String op) {
    if (_currentInput.isNotEmpty) {
      if (_operator.isNotEmpty) {
        _calculate();
      }
      _firstOperand = double.tryParse(_display) ?? 0;
    }

    setState(() {
      _operator = op;
      _shouldResetDisplay = true;
    });
  }

  void _calculate() {
    if (_operator.isEmpty) return;

    final secondOperand = double.tryParse(_display) ?? 0;
    double result;

    switch (_operator) {
      case '+':
        result = _firstOperand + secondOperand;
        break;
      case '-':
        result = _firstOperand - secondOperand;
        break;
      case '×':
        result = _firstOperand * secondOperand;
        break;
      case '÷':
        if (secondOperand == 0) {
          setState(() {
            _display = 'Ошибка';
            _currentInput = '';
            _operator = '';
            _firstOperand = 0;
          });
          return;
        }
        result = _firstOperand / secondOperand;
        break;
      default:
        return;
    }

    setState(() {
      _display = _formatNumber(result);
      _currentInput = '';
      _operator = '';
      _firstOperand = result;
      _shouldResetDisplay = true;
    });
  }

  void _addDecimal() {
    if (_shouldResetDisplay) {
      setState(() {
        _display = '0.';
        _currentInput = '0.';
        _shouldResetDisplay = false;
      });
      return;
    }

    if (_display.contains('.')) return;

    setState(() {
      _display = '$_display.';
      _currentInput = _display;
    });
  }

  void _addDigit(String digit) {
    if (_shouldResetDisplay) {
      setState(() {
        _display = digit;
        _currentInput = digit;
        _shouldResetDisplay = false;
      });
      return;
    }

    // Limit display length
    if (_display.length >= 12) return;

    setState(() {
      if (_display == '0' && digit != '0') {
        _display = digit;
      } else if (_display != '0') {
        _display = _display + digit;
      }
      _currentInput = _display;
    });
  }

  String _formatNumber(double value) {
    // Check if it's a whole number
    if (value == value.truncateToDouble()) {
      return value.toInt().toString();
    }

    // Format with up to 8 decimal places, removing trailing zeros
    String formatted = value.toStringAsFixed(8);
    
    // Remove trailing zeros after decimal point
    while (formatted.contains('.') && formatted.endsWith('0')) {
      formatted = formatted.substring(0, formatted.length - 1);
    }
    
    // Remove trailing decimal point
    if (formatted.endsWith('.')) {
      formatted = formatted.substring(0, formatted.length - 1);
    }

    // Limit total length
    if (formatted.length > 12) {
      return value.toStringAsExponential(4);
    }

    return formatted;
  }
}
