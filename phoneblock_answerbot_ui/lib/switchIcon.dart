import 'package:flutter/material.dart';

final MaterialStateProperty<Icon?> switchIcon = MaterialStateProperty.resolveWith<Icon?>(
      (Set<MaterialState> states) {
    if (states.contains(MaterialState.selected)) {
      return const Icon(Icons.check);
    }
    return const Icon(Icons.close);
  },
);
