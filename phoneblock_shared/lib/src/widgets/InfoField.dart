import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:font_awesome_flutter/font_awesome_flutter.dart';

class InfoField extends StatefulWidget {
  final String label;
  final String? help;
  final String? value;
  final double padding;
  final bool password;
  final bool noCopy;

  const InfoField(this.label, this.value, {
    this.help,
    this.padding = 0,
    this.password = false,
    this.noCopy = false,
    super.key
  });

  @override
  State<StatefulWidget> createState() {
    return InfoFieldState();
  }
}

class InfoFieldState extends State<InfoField> {
  bool shown = false;

  String get text => (widget.password && !shown) ? "************" : (widget.value ?? "<not set>");

  @override
  Widget build(BuildContext context) {
    var help = widget.help;

    var controller = TextEditingController(text: text);

    Widget result = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(
              child: TextFormField(
                decoration: InputDecoration(
                  labelText: widget.label,
                ),
                controller: controller,
                readOnly: true,
              ),
            ),
            if (widget.password) IconButton(
                onPressed: () {
                  setState(() {
                    shown = !shown;
                    controller.text = text;
                  });
                },
                icon: shown ?
                  const Icon(FontAwesomeIcons.eyeSlash) :
                  const Icon(FontAwesomeIcons.eye),
              tooltip: shown ? "Versteckt das Passwort." : "Zeigt das Passwort an (Du kannst das Passwort mit dem Kopierknopf kopieren, ohne es vorher anzuzeigen).",
            ),
            if (!widget.noCopy) IconButton(
                onPressed: () async {
                  await Clipboard.setData(ClipboardData(text: widget.value ?? ""));
                  if (!context.mounted) return;
                  ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text("Copied to clipboard.")));
                },
                icon: const Icon(Icons.copy),
                tooltip: "Kopiert den Wert in die Zwischenablage.",
            ),
          ],
        ),
        if (help != null) Text(help, style: const TextStyle(fontSize: 12, color: Colors.black54))
      ],
    );

    return widget.padding == 0 ?
      result :
      Padding(
        padding: EdgeInsets.only(top: widget.padding),
        child: result);
  }
}
