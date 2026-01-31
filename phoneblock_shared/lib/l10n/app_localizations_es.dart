// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Spanish Castilian (`es`).
class AppLocalizationsEs extends AppLocalizations {
  AppLocalizationsEs([String locale = 'es']) : super(locale);

  @override
  String get appTitle => 'Contestador automático PhoneBlock';

  @override
  String get yourAnswerbots => 'Su contestador automático';

  @override
  String get loginRequired => 'Inscripción obligatoria';

  @override
  String get login => 'Inicio de sesión';

  @override
  String get loadingData => 'Cargando datos...';

  @override
  String get refreshingData => 'Actualizar datos...';

  @override
  String get noAnswerbotsYet =>
      'Si todavía no tiene un contestador automático, haga clic en el botón más abajo para crear un contestador automático PhoneBlock.';

  @override
  String get createAnswerbot => 'Crear contestador automático';

  @override
  String answerbotName(String userName) {
    return 'Contestador automático $userName';
  }

  @override
  String answerbotStats(int newCalls, int talkTimeSeconds, int callsAccepted) {
    return '$newCalls llamadas nuevas, $callsAccepted llamadas, $talkTimeSeconds s tiempo total de conversación.';
  }

  @override
  String get statusActive => 'activo';

  @override
  String get statusConnecting => 'conectar...';

  @override
  String get statusDisabled => 'apagado';

  @override
  String get statusIncomplete => 'incompleto';

  @override
  String get deleteAnswerbot => 'Borrar contestador automático';

  @override
  String get enabled => 'Activado';

  @override
  String get minVotes => 'Votos mínimos';

  @override
  String get minVotesDescription =>
      '¿Cuál es el número mínimo de voces necesario para que un número sea aceptado por el contestador automático?';

  @override
  String get minVotes2 => '2 - bloquear inmediatamente';

  @override
  String get minVotes4 => '4 - Esperar confirmación';

  @override
  String get minVotes10 => '10 - sólo cuando sea seguro';

  @override
  String get minVotes100 => '100 - sólo los mejores spammers';

  @override
  String get cannotChangeWhileEnabled =>
      'Sólo se puede cambiar cuando el contestador automático está apagado.';

  @override
  String get saveSettings => 'Guardar ajustes';

  @override
  String get retentionPeriod => 'Tiempo de almacenamiento';

  @override
  String get retentionPeriodDescription =>
      '¿Cuánto tiempo deben conservarse las llamadas?';

  @override
  String get retentionNever => 'No borrar nunca';

  @override
  String get retentionWeek => 'Borrar después de 1 semana';

  @override
  String get retentionMonth => 'Suprimir después de 1 mes';

  @override
  String get retentionQuarter => 'Borrar después de 3 meses';

  @override
  String get retentionYear => 'Suprimir después de 1 año';

  @override
  String get saveRetentionSettings =>
      'Guardar la configuración de almacenamiento';

  @override
  String get showHelp => 'Mostrar ayuda';

  @override
  String get newAnswerbot => 'Nuevo contestador automático';

  @override
  String get usePhoneBlockDynDns => 'Utilizar PhoneBlock DynDNS';

  @override
  String get dynDnsDescription =>
      'PhoneBlock necesita conocer la dirección de Internet de tu buzón Fritz! para poder aceptar llamadas.';

  @override
  String get setupPhoneBlockDynDns => 'Configurar PhoneBlock DynDNS';

  @override
  String get domainName => 'Nombre de dominio';

  @override
  String get domainNameHint =>
      '¡Introduzca el nombre de dominio de su Fritz! Si su Fritz!Box aún no tiene nombre de dominio, active PhoneBlock-DynDNS.';

  @override
  String get checkDomainName => 'Comprobar nombres de dominio';

  @override
  String get setupDynDns => 'Configurar DynDNS';

  @override
  String get dynDnsInstructions =>
      'En la configuración de su Fritz!Box, abra la página Internet > Acciones > DynDNS e introduzca allí los siguientes valores:';

  @override
  String get checkDynDns => 'Comprobar DynDNS';

  @override
  String get createAnswerbotTitle => 'Crear contestador automático';

  @override
  String get registerAnswerbot => 'Registrar contestador automático';

  @override
  String get answerbotRegistered => 'Contestador automático registrado';

  @override
  String get close => 'Cerrar';

  @override
  String get error => 'Error';

  @override
  String cannotLoadInfo(String message, int statusCode) {
    return 'No se puede recuperar la información (error $statusCode): $message';
  }

  @override
  String wrongContentType(String contentType) {
    return 'No se puede recuperar la información (Content-Type: $contentType).';
  }

  @override
  String get callList => 'Lista de llamadas';

  @override
  String get clearCallList => 'Borrar lista de llamadas';

  @override
  String get noCalls => 'Aún no hay llamadas';

  @override
  String get answerbot => 'Contestador automático';

  @override
  String get answerbotSettings => 'Configuración del contestador automático';

  @override
  String get minConfidence => 'Confianza mínima';

  @override
  String get minConfidenceHelp =>
      'Cuántas reclamaciones son necesarias para que un número sea interceptado por el contestador automático.';

  @override
  String get blockNumberRanges => 'Rangos de números de bloque';

  @override
  String get blockNumberRangesHelp =>
      'Acepta la llamada incluso para un número que todavía no se sabe que es SPAM si hay razones para sospechar que el número pertenece a una conexión del sistema desde la que se origina el SPAM.';

  @override
  String get preferIPv4 => 'Favorecer la comunicación IPv4';

  @override
  String get preferIPv4Help =>
      'Si está disponible, la comunicación con el contestador automático se realiza a través de IPv4. Parece que hay conexiones telefónicas para las que no es posible una conexión de voz a través de IPv6, aunque se disponga de una dirección IPv6.';

  @override
  String get callRetention => 'Retención de llamadas';

  @override
  String get automaticDeletion => 'Eliminación automática';

  @override
  String get automaticDeletionHelp =>
      '¿Después de qué tiempo deben borrarse automáticamente los registros de llamadas antiguos? No borrar nunca desactiva el borrado automático.';

  @override
  String get dnsSettings => 'Configuración DNS';

  @override
  String get dnsSetting => 'Configuración DNS';

  @override
  String get phoneBlockDns => 'PhoneBlock DNS';

  @override
  String get otherProviderOrDomain => 'Otro proveedor o nombre de dominio';

  @override
  String get dnsSettingHelp =>
      'Cómo el contestador automático encuentra tu buzón Fritz! en Internet.';

  @override
  String get updateUrl => 'Actualizar URL';

  @override
  String get updateUrlHelp =>
      '¡Nombre de usuario para el recurso compartido DynDNS en su Fritz!';

  @override
  String get domainNameHelp => 'Nombre que recibe tu buzón Fritz! en Internet.';

  @override
  String get dyndnsUsername => 'Nombre de usuario DynDNS';

  @override
  String get dyndnsUsernameHelp =>
      '¡Nombre de usuario para el recurso compartido DynDNS en su Fritz!';

  @override
  String get dyndnsPassword => 'Contraseña DynDNS';

  @override
  String get dyndnsPasswordHelp =>
      'La contraseña que debe utilizar para compartir DynDNS.';

  @override
  String get host => 'Anfitrión';

  @override
  String get hostHelp =>
      'El nombre de host a través del cual se puede acceder a su Fritz!Box desde Internet.';

  @override
  String get sipSettings => 'Configuración SIP';

  @override
  String get user => 'Usuario';

  @override
  String get userHelp =>
      'El nombre de usuario que debe configurarse en la Fritz!Box para acceder al dispositivo de telefonía.';

  @override
  String get password => 'Contraseña';

  @override
  String get passwordHelp =>
      '¡La contraseña que debe asignarse para acceder al aparato de telefonía en el Fritz!';

  @override
  String get savingSettings => 'Guardar ajustes...';

  @override
  String get errorSavingSettings => 'Error al guardar la configuración.';

  @override
  String savingFailed(String message) {
    return 'Error al guardar: $message';
  }

  @override
  String get enableAfterSavingFailed =>
      'Volver a encender después de guardar falló';

  @override
  String get enablingAnswerbot => 'Enciende el contestador...';

  @override
  String get errorEnablingAnswerbot =>
      'Error al encender el contestador automático.';

  @override
  String cannotEnable(String message) {
    return 'No se puede encender: $message';
  }

  @override
  String get enablingFailed => 'No se enciende el contestador automático';

  @override
  String enablingFailedMessage(String message) {
    return 'Error en el encendido: $message';
  }

  @override
  String retryingMessage(String message) {
    return '$message Inténtalo de nuevo...';
  }

  @override
  String get savingRetentionSettings =>
      'Guardar la configuración de almacenamiento...';

  @override
  String get errorSavingRetentionSettings =>
      'Error al guardar la configuración de almacenamiento.';

  @override
  String get automaticDeletionDisabled => 'Eliminación automática desactivada';

  @override
  String retentionSettingsSaved(String period) {
    return 'Ajustes de almacenamiento guardados ($period)';
  }

  @override
  String get oneWeek => '1 semana';

  @override
  String get oneMonth => '1 mes';

  @override
  String get threeMonths => '3 meses';

  @override
  String get oneYear => '1 año';

  @override
  String get never => 'Nunca';

  @override
  String deleteAnswerbotConfirm(String userName) {
    return '¿Debería borrarse realmente el $userName del contestador automático?';
  }

  @override
  String get cancel => 'Cancelar';

  @override
  String get delete => 'Borrar';

  @override
  String get deletionFailed => 'Eliminación fallida';

  @override
  String get answerbotCouldNotBeDeleted =>
      'No se ha podido borrar el contestador automático';

  @override
  String get spamCalls => 'Llamadas SPAM';

  @override
  String get deleteCalls => 'Borrar llamadas';

  @override
  String get deletingCallsFailed => 'Eliminación fallida';

  @override
  String get deleteRequestFailed =>
      'No se ha podido procesar la solicitud de borrado.';

  @override
  String cannotRetrieveCalls(String message, int statusCode) {
    return 'No se pueden recuperar las llamadas (error $statusCode): $message';
  }

  @override
  String get noNewCalls => 'No hay nuevas llamadas.';

  @override
  String duration(int seconds) {
    return 'Duración $seconds s';
  }

  @override
  String today(String time) {
    return 'Hoy $time';
  }

  @override
  String yesterday(String time) {
    return 'Ayer $time';
  }

  @override
  String get dynDnsDescriptionLong =>
      'PhoneBlock necesita conocer la dirección de Internet de su buzón Fritz! para poder registrar el contestador automático en su buzón Fritz! Si ya ha configurado MyFRITZ! u otro proveedor de DynDNS, puede utilizar este nombre de dominio. Si no, puede simplemente configurar DynDNS desde PhoneBlock, y luego activar este interruptor.';

  @override
  String get setupPhoneBlockDynDnsSnackbar => 'Configure PhoneBlock DynDNS.';

  @override
  String get setupFailed => 'Error de configuración';

  @override
  String cannotSetupDynDns(String message) {
    return 'No se puede configurar DynDNS: $message';
  }

  @override
  String get domainname => 'Nombre de dominio';

  @override
  String get domainNameHintLong =>
      'Nombre de dominio de su buzón Fritz! (dirección MyFRITZ! o nombre de dominio DynDNS)';

  @override
  String get inputCannotBeEmpty => 'La entrada no debe estar vacía.';

  @override
  String get invalidDomainName => 'No hay nombre de dominio válido.';

  @override
  String get domainNameTooLong => 'El nombre de dominio es demasiado largo.';

  @override
  String get domainNameHintExtended =>
      '¡Introduzca el nombre de dominio de su Fritz! Si su Fritz!Box aún no tiene un nombre de dominio, active PhoneBlock DynDNS. Puede encontrar el nombre de dominio de su Fritz!Box en (En Internet > Acciones > DynDNS). También puede introducir la dirección MyFRITZ! (Internet > Cuenta MyFRITZ!), por ejemplo z4z...l4n.myfritz.net.';

  @override
  String get checkingDomainName => 'Compruebe los nombres de dominio.';

  @override
  String domainNameNotAccepted(String message) {
    return 'No se ha aceptado el nombre de dominio: $message.';
  }

  @override
  String get dynDnsInstructionsLong =>
      'Abra la página Internet > Recursos compartidos > DynDNS en la configuración de su Fritz!Box e introduzca la información que aparece aquí.';

  @override
  String get updateUrlHelp2 =>
      'La URL que su Fritz! box llama para dar a PhoneBlock su dirección de Internet. Introduzca la URL exactamente como está escrita aquí. No reemplace los valores en los corchetes angulares, su caja de Fritz! lo hará automáticamente cuando la llame. Es mejor usar la función copiar para copiar los valores.';

  @override
  String get domainNameHelp2 =>
      'Este nombre de dominio no se puede resolver públicamente más tarde. Su dirección de Internet sólo será compartida con PhoneBlock.';

  @override
  String get username => 'Nombre de usuario';

  @override
  String get usernameHelp =>
      'El nombre de usuario con el que tu buzón Fritz! se conecta a PhoneBlock para dar a conocer su dirección de Internet.';

  @override
  String get passwordLabel => 'Contraseña';

  @override
  String get passwordHelp2 =>
      'La contraseña con la que su Fritz!Box se registra en PhoneBlock para dar a conocer su dirección de Internet. Por razones de seguridad, no puede introducir su propia contraseña, sino que debe utilizar la contraseña generada de forma segura por PhoneBlock.';

  @override
  String get checkingDynDns => 'Compruebe la configuración de DynDNS.';

  @override
  String get notRegistered => 'No registrado';

  @override
  String fritzBoxNotRegistered(String message) {
    return 'Su Fritz!Box aún no se ha registrado en PhoneBlock, DynDNS no está actualizado: $message.';
  }

  @override
  String get sipSetupInstructions =>
      'Ahora configure el contestador automático PhoneBlock como \"Teléfono (con y sin contestador automático)\". Para asegurarse de que funciona, siga exactamente los pasos que se indican a continuación:';

  @override
  String get sipSetupStep1 =>
      '1. Abra la página Telefonía > Dispositivos de telefonía en la configuración de su Fritz!Box y haga clic en el botón \"Configurar nuevo dispositivo\".';

  @override
  String get sipSetupStep2 =>
      '2. Seleccione la opción \"Teléfono (con y sin contestador automático)\" y pulse \"Siguiente\".';

  @override
  String get sipSetupStep3 =>
      '3. Seleccione la opción \"LAN/WLAN (teléfono IP)\", asigne al teléfono el nombre \"PhoneBlock\" y pulse \"Siguiente\".';

  @override
  String get sipSetupStep4 =>
      '4. Introduzca ahora el nombre de usuario y la contraseña de su contestador automático y haga clic en \"Siguiente\".';

  @override
  String get usernameHelp2 =>
      '¡El nombre de usuario con el que el contestador automático PhoneBlock se conecta a su Fritz!';

  @override
  String get passwordHelp3 =>
      '¡La contraseña que el contestador automático PhoneBlock utiliza para iniciar sesión en su Fritz! PhoneBlock ha generado una contraseña segura para usted.';

  @override
  String get sipSetupStep5 =>
      '5. el número de teléfono consultado ahora no importa, el contestador PhoneBlock no realiza activamente ninguna llamada, sino que sólo acepta llamadas SPAM. El número de teléfono se deselecciona de nuevo en el paso 9. Simplemente haga clic en \"Siguiente\" aquí.';

  @override
  String get sipSetupStep6 =>
      '6. Seleccione \"Aceptar todas las llamadas\" y haga clic en \"Siguiente\". De todas formas, el contestador PhoneBlock sólo acepta llamadas si el número del llamante está en la lista de bloqueados. Al mismo tiempo, PhoneBlock nunca acepta llamadas de números que estén en su agenda normal.';

  @override
  String get sipSetupStep7 =>
      '7. Verá un resumen. Los ajustes son (casi) completa, haga clic en \"Aplicar\".';

  @override
  String get sipSetupStep8 =>
      '8. Ahora verá \"PhoneBlock\" en la lista de dispositivos de telefonía. Todavía faltan algunos ajustes que sólo podrá realizar más tarde. Por lo tanto, haga clic en el lápiz de edición en la línea del contestador automático PhoneBlock.';

  @override
  String get sipSetupStep9 =>
      '9. seleccione la última opción (vacía) en el campo \"Llamadas salientes\", ya que PhoneBlock nunca realiza llamadas salientes y, por lo tanto, el contestador automático no necesita un número para las llamadas salientes.';

  @override
  String get sipSetupStep10 =>
      '10. Seleccione la pestaña \"Datos de acceso\". 11. Confirme la respuesta haciendo clic en \"Aplicar\". ¡11. Seleccione ahora la opción \"Permitir inicio de sesión desde Internet\" para que el contestador automático PhoneBlock de la nube PhoneBlock pueda iniciar sesión en su Fritz! Debe introducir de nuevo la contraseña del contestador automático (véase más arriba) en el campo \"Contraseña\" antes de hacer clic en \"Aplicar\". Primero borre los asteriscos del campo.';

  @override
  String get sipSetupStep11 =>
      '11. aparece un mensaje que le advierte de que podrían establecerse conexiones de pago a través del acceso a Internet. Puede confirmarlo tranquilamente, en primer lugar porque PhoneBlock nunca establece conexiones activamente, en segundo lugar porque PhoneBlock ha creado una contraseña segura para usted (véase más arriba) para que nadie más pueda conectarse y en tercer lugar porque ha desactivado las conexiones salientes en el paso 9. Dependiendo de la configuración de su Fritz!Box, puede que necesite confirmar la configuración en un teléfono DECT conectado directamente al Fritz!';

  @override
  String get sipSetupStep12 =>
      '12. Ahora ya está todo hecho. Pulse Atrás para volver a la lista de aparatos de telefonía. Ahora puede activar su contestador automático con el botón de la parte inferior.';

  @override
  String get tryingToRegisterAnswerbot =>
      'Intenta registrar el contestador automático...';

  @override
  String get answerbotRegistrationFailed =>
      'Fallo en el registro del contestador automático';

  @override
  String registrationFailed(String message) {
    return 'Error de registro: $message';
  }

  @override
  String get answerbotRegisteredSuccess =>
      'Su contestador automático PhoneBlock ha sido registrado con éxito. Las próximas personas que llamen pueden hablar ahora con PhoneBlock. Si desea probar el contestador automático PhoneBlock usted mismo, marque el número interno del dispositivo de telefonía \"PhoneBlock\" que ha configurado. El número interno suele empezar por \"**\".';
}
