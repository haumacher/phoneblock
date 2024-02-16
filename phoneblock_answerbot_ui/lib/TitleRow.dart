import 'package:flutter/cupertino.dart';

class TitleRow extends StatelessWidget {
  final String title;

  const TitleRow(this.title, {super.key});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Padding(padding: const EdgeInsets.only(right: 16),
            child: Image.asset('assets/images/ab-logo-appbar.png', height: 48)
        ),
        Text(title, overflow: TextOverflow.ellipsis,)],
    );
  }
}
