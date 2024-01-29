import 'package:flutter/material.dart';
import 'package:phoneblock_answerbot_ui/AnswerBotList.dart';

void main() {
  runApp(const MyApp());
}

const Color pbColor = Color(0xFF00d1b2);

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'PhoneBlock',
      theme: ThemeData(
        primaryColorLight: pbColor,
        appBarTheme: const AppBarTheme(
          color: pbColor,
          foregroundColor: Colors.white,
        ),
        primaryColor: pbColor,
        floatingActionButtonTheme: const FloatingActionButtonThemeData(
          backgroundColor: pbColor,
          foregroundColor: Colors.white,
        ),
      ),
      home: const HomeScreen(),
    );
  }
}

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return const AnswerBotList();
  }
}


