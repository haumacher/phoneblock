import 'package:flutter/material.dart';
import 'package:phoneblock_shared/phoneblock_shared.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'PhoneBlock Answerbot',
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
      ),
      home: const AnswerBotList(),
    );
  }
}


