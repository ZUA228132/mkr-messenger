import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';

/// Simple fake calculator screen for stealth mode
/// Requirements: 5.2 - Display functional fake calculator interface
class SimpleCalculatorScreen extends StatefulWidget {
  final String secretCode;
  final VoidCallback? onSecretCodeEntered;

  const SimpleCalculatorScreen({
    super.key,
    required this.secretCode,
    this.onSecretCodeEntered,
  });

  @override
  State<SimpleCalculatorScreen> createState() => _SimpleCalculatorScreenState();
}

class _SimpleCalculatorScreenState extends State<SimpleCalculatorScreen> {
  String _display = '0';
  String _currentInput = '';
  String _operator = '';
  double _firstOperand = 0;
  bool _shouldResetDisplay = false;
  String _inputSequence = '';

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      backgroundColor: CupertinoColors.black,
      child: SafeArea(
        child: Column(
          children: [
            Expanded(flex: 2, child: _buildDisplay()),
            Expanded(flex: 5, child: _buildButtonGrid()),
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
            child: _buildButton(button, isOperator: isOperator, isFunction: isFunction),
          );
        }).toList(),
      ),
    );
  }

  Widget _buildButton(String label, {bool isOperator = false, bool isFunction = false}) {
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
            style: TextStyle(fontSize: 32, fontWeight: FontWeight.w400, color: textColor),
          ),
        ),
      ),
    );
  }

  void _onButtonPressed(String button) {
    HapticFeedback.lightImpact();
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
    if (RegExp(r'[0-9]').hasMatch(button)) {
      _inputSequence += button;
      if (_inputSequence.length > 20) {
        _inputSequence = _inputSequence.substring(_inputSequence.length - 20);
      }
      _checkSecretCode();
    } else if (button == 'C') {
      _inputSequence = '';
    } else if (button == '=') {
      _checkSecretCode();
      _inputSequence = '';
    }
  }

  void _checkSecretCode() {
    if (_inputSequence.endsWith(widget.secretCode)) {
      HapticFeedback.heavyImpact();
      widget.onSecretCodeEntered?.call();
    }
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
      _display = _display.startsWith('-') ? _display.substring(1) : '-$_display';
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
      if (_operator.isNotEmpty) _calculate();
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
            _display = 'Error';
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
    if (value == value.truncateToDouble()) {
      return value.toInt().toString();
    }
    String formatted = value.toStringAsFixed(8);
    while (formatted.contains('.') && formatted.endsWith('0')) {
      formatted = formatted.substring(0, formatted.length - 1);
    }
    if (formatted.endsWith('.')) {
      formatted = formatted.substring(0, formatted.length - 1);
    }
    if (formatted.length > 12) {
      return value.toStringAsExponential(4);
    }
    return formatted;
  }
}
