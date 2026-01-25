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
}
