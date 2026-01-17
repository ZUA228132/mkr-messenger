import 'package:flutter/cupertino.dart';

/// Terms of Use screen
class TermsOfUseScreen extends StatelessWidget {
  const TermsOfUseScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      navigationBar: CupertinoNavigationBar(
        middle: const Text('Условия использования'),
      ),
      child: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Условия использования MKR Messenger',
                style: TextStyle(
                  fontSize: 24,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 24),
              _buildSection(
                '1. Принятие условий',
                'Используя MKR Messenger, вы соглашаетесь с этими условиями. Если вы не согласны с этими условиями, пожалуйста, не используйте приложение.',
              ),
              const SizedBox(height: 20),
              _buildSection(
                '2. Возраст',
                'Вы должны быть не моложе 13 лет для использования этого приложения. Используя приложение, вы подтверждаете, что вам исполнилось 13 лет.',
              ),
              const SizedBox(height: 20),
              _buildSection(
                '3. Безопасность',
                'MKR Messenger использует сквозное шифрование для защиты ваших сообщений. Однако вы несете ответственность за безопасность своего устройства и учётных данных.',
              ),
              const SizedBox(height: 20),
              _buildSection(
                '4. Запрещённые действия',
                'Запрещено:\n'
                '• Использовать приложение для незаконных целей\n'
                '• Распространять вредоносное ПО\n'
                '• Пытаться получить несанкционированный доступ к системам\n'
                '• Нарушать законы вашей страны',
              ),
              const SizedBox(height: 20),
              _buildSection(
                '5. Конфиденциальность',
                'Мы уважаем вашу конфиденциальность. Подробную информацию см. в Политике конфиденциальности.',
              ),
              const SizedBox(height: 20),
              _buildSection(
                '6. Ограничение ответственности',
                'MKR Messenger предоставляется "как есть". Мы не несём ответственности за убытки, возникшие в результате использования приложения.',
              ),
              const SizedBox(height: 20),
              _buildSection(
                '7. Изменения условий',
                'Мы оставляем за собой право изменять эти условия в любое время. Продолжая использовать приложение после изменений, вы принимаете новые условия.',
              ),
              const SizedBox(height: 32),
              Center(
                child: Text(
                  'Последнее обновление: Январь 2025',
                  style: TextStyle(
                    fontSize: 13,
                    color: CupertinoColors.secondaryLabel.resolveFrom(context),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSection(String title, String content) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: const TextStyle(
            fontSize: 18,
            fontWeight: FontWeight.w600,
          ),
        ),
        const SizedBox(height: 8),
        Text(
          content,
          style: const TextStyle(
            fontSize: 15,
            height: 1.5,
          ),
        ),
      ],
    );
  }
}

/// Privacy Policy screen
class PrivacyPolicyScreen extends StatelessWidget {
  const PrivacyPolicyScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      navigationBar: CupertinoNavigationBar(
        middle: const Text('Политика конфиденциальности'),
      ),
      child: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Политика конфиденциальности',
                style: TextStyle(
                  fontSize: 24,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                'MKR Messenger',
                style: TextStyle(
                  fontSize: 16,
                  color: CupertinoColors.secondaryLabel.resolveFrom(context),
                ),
              ),
              const SizedBox(height: 24),
              _buildSection(
                '1. Сбор данных',
                'Мы собираем минимальную информацию, необходимую для работы приложения:\n'
                '• Учётные данные (username, displayName)\n'
                '• Технические данные (тип устройства, версия ОС)\n'
                '• Данные использования для улучшения сервиса',
              ),
              const SizedBox(height: 20),
              _buildSection(
                '2. Шифрование',
                'Все сообщения защищены сквозным шифрованием (End-to-End Encryption). '
                'Мы не можем читать ваши сообщения.',
              ),
              const SizedBox(height: 20),
              _buildSection(
                '3. Хранение данных',
                '• Сообщения хранятся только на ваших устройствах\n'
                '• Метаданные хранятся на зашищённых серверах\n'
                '• Вы можете удалить все данные в любой момент',
              ),
              const SizedBox(height: 20),
              _buildSection(
                '4. Передача данных',
                'Мы не продаём ваши данные третьим лицам. '
                'Данные передаются только:\n'
                '• Для выполнения законодательства\n'
                '• Для защиты прав и собственности',
              ),
              const SizedBox(height: 20),
              _buildSection(
                '5. Ваши права',
                'Вы имеете право:\n'
                '• Доступ к вашим данным\n'
                '• Запрашивать удаление данных\n'
                '• Экспортировать свои данные\n'
                '• Отозвать согласие на обработку',
              ),
              const SizedBox(height: 20),
              _buildSection(
                '6. Безопасность',
                'Мы используем современные методы защиты:\n'
                '• Сквозное шифрование\n'
                '• Защищённые протоколы передачи\n'
                '• Регулярные проверки безопасности',
              ),
              const SizedBox(height: 20),
              _buildSection(
                '7. Дети',
                'MKR Messenger не предназначен для детей младше 13 лет. '
                'Мы не собираем данные детей сознательно.',
              ),
              const SizedBox(height: 20),
              _buildSection(
                '8. Изменения политики',
                'Мы можем обновлять эту политику. Об изменениях будет сообщено в приложении.',
              ),
              const SizedBox(height: 20),
              _buildSection(
                '9. Контакты',
                'По вопросам конфиденциальности:\n'
                'Email: privacy@mkr-messenger.com\n'
                'Telegram: @mkr_support',
              ),
              const SizedBox(height: 32),
              Center(
                child: Text(
                  'Последнее обновление: Январь 2025',
                  style: TextStyle(
                    fontSize: 13,
                    color: CupertinoColors.secondaryLabel.resolveFrom(context),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSection(String title, String content) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: const TextStyle(
            fontSize: 18,
            fontWeight: FontWeight.w600,
          ),
        ),
        const SizedBox(height: 8),
        Text(
          content,
          style: const TextStyle(
            fontSize: 15,
            height: 1.5,
          ),
        ),
      ],
    );
  }
}
