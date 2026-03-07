import 'package:flutter/material.dart';

final WidgetStateProperty<Icon?> switchIcon = WidgetStateProperty.resolveWith<Icon?>(
      (Set<WidgetState> states) {
    if (states.contains(WidgetState.selected)) {
      return const Icon(Icons.check);
    }
    return const Icon(Icons.close);
  },
);