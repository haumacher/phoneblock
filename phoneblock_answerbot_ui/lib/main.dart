import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:phoneblock_shared/phoneblock_shared.dart';
import 'auth.dart';
import 'answerbot_home.dart';

void main() {
  // Set up auth provider for answerbot app
  setAuthProvider(getStoredAuthToken);

  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'PhoneBlock Answerbot',
      localizationsDelegates: const [
        answerbotLocalizationsDelegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: answerbotSupportedLocales,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color.fromARGB(255, 0, 209, 178),
        ),
        appBarTheme: const AppBarTheme(
          backgroundColor: Color.fromARGB(255, 0, 209, 178),
          foregroundColor: Colors.white,
        ),
      ),
      home: const AnswerbotHome(),
    );
  }
}


