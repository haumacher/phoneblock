import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class InfoField extends StatelessWidget {
  final String label;
  final String? help;
  final String? value;

  const InfoField(this.label, this.value, {this.help, super.key});

  @override
  Widget build(BuildContext context) {
    var help = this.help;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(
              child: TextFormField(
                decoration: InputDecoration(
                  labelText: label,
                ),
                initialValue: value ?? "<not set>",
                readOnly: true,
              ),
            ),
            IconButton(
                onPressed: () async {
                  await Clipboard.setData(ClipboardData(text: value ?? ""));
                  if (!context.mounted) return;
                  ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text("Copied to clipboard.")));
                },
                icon: const Icon(Icons.copy)
            ),
          ],
        ),
        if (help != null) Text(help, style: const TextStyle(fontSize: 12, color: Colors.black54))
      ],
    );
  }
}
