import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:mkr_flutter/main.dart';

void main() {
  testWidgets('MKR App smoke test', (WidgetTester tester) async {
    // Build our app and trigger a frame.
    await tester.pumpWidget(const ProviderScope(child: MKRApp()));

    // Verify that the app title is displayed
    expect(find.text('MKR Messenger'), findsWidgets);
  });
}
