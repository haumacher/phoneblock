// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for French (`fr`).
class AppLocalizationsFr extends AppLocalizations {
  AppLocalizationsFr([String locale = 'fr']) : super(locale);

  @override
  String get appTitle => 'PhoneBlock répondeur téléphonique';

  @override
  String get yourAnswerbots => 'Ton répondeur';

  @override
  String get loginRequired => 'Inscription nécessaire';

  @override
  String get login => 'Connexion';

  @override
  String get loadingData => 'Chargement des données...';

  @override
  String get refreshingData => 'Mettre à jour les données...';

  @override
  String get noAnswerbotsYet =>
      'Tu n\'as pas encore de répondeur, clique sur le bouton plus ci-dessous pour créer un répondeur PhoneBlock.';

  @override
  String get createAnswerbot => 'Créer un répondeur téléphonique';

  @override
  String answerbotName(String userName) {
    return 'Répondeur $userName';
  }

  @override
  String answerbotStats(int newCalls, int talkTimeSeconds, int callsAccepted) {
    return '$newCalls nouveaux appels, $callsAccepted appels, $talkTimeSeconds s temps de conversation total';
  }

  @override
  String get statusActive => 'actif';

  @override
  String get statusConnecting => 'relie...';

  @override
  String get statusDisabled => 'désactivé';

  @override
  String get statusIncomplete => 'incomplet';

  @override
  String get deleteAnswerbot => 'Effacer le répondeur';

  @override
  String get enabled => 'Activé';

  @override
  String get minVotes => 'Minimum de voix';

  @override
  String get minVotesDescription =>
      'Combien de voix au moins un numéro doit-il avoir pour que le répondeur l\'accepte ?';

  @override
  String get minVotes2 => '2 - bloquer immédiatement';

  @override
  String get minVotes4 => '4 - Attendre la confirmation';

  @override
  String get minVotes10 => '10 - seulement si sûr';

  @override
  String get minVotes100 => '100 - seulement les meilleurs spammeurs';

  @override
  String get cannotChangeWhileEnabled =>
      'Ne peut être modifié que si le répondeur est désactivé.';

  @override
  String get saveSettings => 'Enregistrer les paramètres';

  @override
  String get retentionPeriod => 'Durée de conservation';

  @override
  String get retentionPeriodDescription =>
      'Combien de temps les appels doivent-ils être conservés ?';

  @override
  String get retentionNever => 'Ne jamais supprimer';

  @override
  String get retentionWeek => 'Supprimer après 1 semaine';

  @override
  String get retentionMonth => 'Supprimer après 1 mois';

  @override
  String get retentionQuarter => 'Supprimer après 3 mois';

  @override
  String get retentionYear => 'Supprimer après 1 an';

  @override
  String get saveRetentionSettings =>
      'Enregistrer les paramètres de conservation';

  @override
  String get showHelp => 'Afficher l\'aide';

  @override
  String get newAnswerbot => 'Nouveau répondeur';

  @override
  String get usePhoneBlockDynDns => 'Utiliser PhoneBlock-DynDNS';

  @override
  String get dynDnsDescription =>
      'PhoneBlock doit connaître l\'adresse Internet de ta Fritz!Box pour pouvoir prendre des appels.';

  @override
  String get setupPhoneBlockDynDns => 'Configurer PhoneBlock-DynDNS';

  @override
  String get domainName => 'Nom de domaine';

  @override
  String get domainNameHint =>
      'Indique le nom de domaine de ta Fritz!Box. Si ta Fritz!Box n\'a pas encore de nom de domaine, active PhoneBlock-DynDNS.';

  @override
  String get checkDomainName => 'Vérifier les noms de domaine';

  @override
  String get setupDynDns => 'Configurer DynDNS';

  @override
  String get dynDnsInstructions =>
      'Dans les paramètres de ta Fritz!Box, ouvre la page Internet > Partages > DynDNS et saisis les valeurs suivantes :';

  @override
  String get checkDynDns => 'Vérifier le DynDNS';

  @override
  String get createAnswerbotTitle => 'Créer un répondeur téléphonique';

  @override
  String get registerAnswerbot => 'Enregistrer un répondeur téléphonique';

  @override
  String get answerbotRegistered => 'Répondeur enregistré';

  @override
  String get close => 'Fermer';

  @override
  String get error => 'Erreur';

  @override
  String cannotLoadInfo(String message, int statusCode) {
    return 'Impossible de récupérer les informations (erreur $statusCode) : $message';
  }

  @override
  String wrongContentType(String contentType) {
    return 'Les informations ne peuvent pas être récupérées (Content-Type : $contentType).';
  }

  @override
  String get callList => 'Liste d\'appels';

  @override
  String get clearCallList => 'Effacer le journal des appels';

  @override
  String get noCalls => 'Pas encore d\'appels';

  @override
  String get answerbot => 'Répondeur téléphonique';

  @override
  String get answerbotSettings => 'Paramètres du répondeur téléphonique';

  @override
  String get minConfidence => 'Confiance minimale';

  @override
  String get minConfidenceHelp =>
      'Combien de plaintes sont nécessaires pour qu\'un numéro soit intercepté par le répondeur.';

  @override
  String get blockNumberRanges => 'Bloquer des plages de numéros';

  @override
  String get blockNumberRangesHelp =>
      'Accepte l\'appel même pour un numéro qui n\'est pas encore connu comme SPAM, s\'il y a lieu de supposer que le numéro appartient à un raccordement d\'installation d\'où provient le SPAM.';

  @override
  String get preferIPv4 => 'Privilégier la communication IPv4';

  @override
  String get preferIPv4Help =>
      'Si elle est disponible, la communication avec le répondeur se fait via IPv4. Il semble qu\'il y ait des lignes téléphoniques pour lesquelles la connexion vocale via IPv6 n\'est pas possible, bien qu\'une adresse IPv6 soit disponible.';

  @override
  String get callRetention => 'Réserve d\'appels';

  @override
  String get automaticDeletion => 'Suppression automatique';

  @override
  String get automaticDeletionHelp =>
      'Après combien de temps les anciens journaux d\'appels doivent-ils être automatiquement supprimés ? Ne jamais effacer désactive l\'effacement automatique.';

  @override
  String get dnsSettings => 'Paramètres DNS';

  @override
  String get dnsSetting => 'Paramètre DNS';

  @override
  String get phoneBlockDns => 'PhoneBlock-DNS';

  @override
  String get otherProviderOrDomain => 'Autre fournisseur ou nom de domaine';

  @override
  String get dnsSettingHelp =>
      'Comment le répondeur trouve ta Fritz!Box sur Internet.';

  @override
  String get updateUrl => 'URL de mise à jour';

  @override
  String get updateUrlHelp =>
      'Nom d\'utilisateur pour la passerelle DynDNS dans ta Fritz!Box.';

  @override
  String get domainNameHelp => 'Nom que ta Fritz!Box reçoit sur Internet.';

  @override
  String get dyndnsUsername => 'Nom d\'utilisateur DynDNS';

  @override
  String get dyndnsUsernameHelp =>
      'Nom d\'utilisateur pour la passerelle DynDNS dans ta Fritz!Box.';

  @override
  String get dyndnsPassword => 'Mot de passe DynDNS';

  @override
  String get dyndnsPasswordHelp =>
      'Le mot de passe que tu dois utiliser pour le partage DynDNS.';

  @override
  String get host => 'Hôte';

  @override
  String get hostHelp =>
      'Le nom d\'hôte par lequel ta Fritz!Box est accessible depuis Internet.';

  @override
  String get sipSettings => 'Paramètres SIP';

  @override
  String get user => 'Utilisateur';

  @override
  String get userHelp =>
      'Le nom d\'utilisateur qui doit être configuré dans la Fritz!Box pour l\'accès à l\'appareil de téléphonie.';

  @override
  String get password => 'Mot de passe';

  @override
  String get passwordHelp =>
      'Le mot de passe qui doit être attribué dans la Fritz!Box pour l\'accès à l\'appareil de téléphonie.';

  @override
  String get savingSettings => 'Enregistrer les paramètres...';

  @override
  String get errorSavingSettings =>
      'Erreur lors de l\'enregistrement des paramètres.';

  @override
  String savingFailed(String message) {
    return 'Echec de l\'enregistrement : $message';
  }

  @override
  String get enableAfterSavingFailed =>
      'Échec de la réactivation après l\'enregistrement';

  @override
  String get enablingAnswerbot => 'Activez le répondeur...';

  @override
  String get errorEnablingAnswerbot =>
      'Erreur lors de la mise en marche du répondeur.';

  @override
  String cannotEnable(String message) {
    return 'Impossible d\'allumer : $message';
  }

  @override
  String get enablingFailed =>
      'Échec de la mise en marche du répondeur téléphonique';

  @override
  String enablingFailedMessage(String message) {
    return 'Échec de la mise en marche : $message';
  }

  @override
  String retryingMessage(String message) {
    return '$message Essaie à nouveau';
  }

  @override
  String get savingRetentionSettings =>
      'Enregistrer les paramètres de conservation...';

  @override
  String get errorSavingRetentionSettings =>
      'Erreur lors de l\'enregistrement des paramètres de conservation.';

  @override
  String get automaticDeletionDisabled => 'Suppression automatique désactivée';

  @override
  String retentionSettingsSaved(String period) {
    return 'Paramètres de conservation enregistrés ($period)';
  }

  @override
  String get oneWeek => '1 semaine';

  @override
  String get oneMonth => '1 mois';

  @override
  String get threeMonths => '3 mois';

  @override
  String get oneYear => '1 an';

  @override
  String get never => 'Jamais';

  @override
  String deleteAnswerbotConfirm(String userName) {
    return 'Faut-il vraiment supprimer le répondeur $userName ?';
  }

  @override
  String get cancel => 'Annuler';

  @override
  String get delete => 'Supprimer';

  @override
  String get deletionFailed => 'Échec de la suppression';

  @override
  String get answerbotCouldNotBeDeleted =>
      'Le répondeur n\'a pas pu être effacé';

  @override
  String get spamCalls => 'Appels SPAM';

  @override
  String get deleteCalls => 'Supprimer des appels';

  @override
  String get deletingCallsFailed => 'Échec de la suppression';

  @override
  String get deleteRequestFailed =>
      'La demande de suppression n\'a pas pu être traitée.';

  @override
  String cannotRetrieveCalls(String message, int statusCode) {
    return 'Les appels ne peuvent pas être récupérés (erreur $statusCode) : $message';
  }

  @override
  String get noNewCalls => 'Pas de nouveaux appels.';

  @override
  String duration(int seconds) {
    return 'Durée $seconds s';
  }

  @override
  String today(String time) {
    return 'Aujourd\'hui $time';
  }

  @override
  String yesterday(String time) {
    return 'Hier $time';
  }

  @override
  String get dynDnsDescriptionLong =>
      'PhoneBlock doit connaître l\'adresse Internet de ta Fritz!Box pour pouvoir enregistrer le répondeur sur ta Fritz!Box. Si tu as déjà configuré MyFRITZ ! ou un autre fournisseur DynDNS, tu peux utiliser ce nom de domaine. Si ce n\'est pas le cas, tu peux tout simplement configurer le DynDNS de PhoneBlock, puis activer ce bouton.';

  @override
  String get setupPhoneBlockDynDnsSnackbar => 'Configure PhoneBlock DynDNS.';

  @override
  String get setupFailed => 'Échec de la configuration';

  @override
  String cannotSetupDynDns(String message) {
    return 'Le DynDNS ne peut pas être configuré : $message';
  }

  @override
  String get domainname => 'Nom de domaine';

  @override
  String get domainNameHintLong =>
      'Nom de domaine de ta Fritz!Box (soit l\'adresse MyFRITZ !, soit le nom de domaine DynDNS)';

  @override
  String get inputCannotBeEmpty => 'L\'entrée ne doit pas être vide.';

  @override
  String get invalidDomainName => 'Pas de nom de domaine valide.';

  @override
  String get domainNameTooLong => 'Le nom de domaine est trop long.';

  @override
  String get domainNameHintExtended =>
      'Indique le nom de domaine de ta Fritz!Box. Si ta Fritz!Box n\'a pas encore de nom de domaine, active le PhoneBlock-DynDNS. Tu trouveras le nom de domaine de ta Fritz!Box sous (sous Internet > Partages > DynDNS). Tu peux aussi indiquer l\'adresse MyFRITZ ! (Internet > Compte MyFRITZ !), par exemple z4z...l4n.myfritz.net.';

  @override
  String get checkingDomainName => 'Vérifie les noms de domaine.';

  @override
  String domainNameNotAccepted(String message) {
    return 'Le nom de domaine n\'a pas été accepté : $message';
  }

  @override
  String get dynDnsInstructionsLong =>
      'Dans les paramètres de ta Fritz!Box, ouvre la page Internet > Partages > DynDNS et saisis les informations indiquées ici.';

  @override
  String get updateUrlHelp2 =>
      'L\'URL que ta Fritz!Box appelle pour communiquer son adresse Internet à PhoneBlock. Saisis l\'URL exactement comme elle est écrite ici. Ne remplace pas les valeurs entre parenthèses, ta Fritz!Box le fait automatiquement lors de l\'appel. Utilise de préférence la fonction de copie pour reprendre les valeurs.';

  @override
  String get domainNameHelp2 =>
      'Ce nom de domaine ne peut pas être résolu publiquement par la suite. Ton adresse Internet sera exclusivement partagée avec PhoneBlock.';

  @override
  String get username => 'Nom d\'utilisateur';

  @override
  String get usernameHelp =>
      'Le nom d\'utilisateur avec lequel ta Fritz!Box se connecte à PhoneBlock pour communiquer son adresse Internet.';

  @override
  String get passwordLabel => 'Mot de passe';

  @override
  String get passwordHelp2 =>
      'Le mot de passe avec lequel ta Fritz!Box se connecte à PhoneBlock pour communiquer son adresse Internet. Pour des raisons de sécurité, tu ne peux pas saisir ton propre mot de passe, mais tu dois utiliser le mot de passe généré de manière sécurisée par PhoneBlock.';

  @override
  String get checkingDynDns => 'Vérifier la configuration DynDNS.';

  @override
  String get notRegistered => 'Non enregistré';

  @override
  String fritzBoxNotRegistered(String message) {
    return 'Ta Fritz!Box ne s\'est pas encore connectée à PhoneBlock, le DynDNS n\'est pas à jour : $message';
  }

  @override
  String get sipSetupInstructions =>
      'Configure maintenant le répondeur PhoneBlock comme \"téléphone (avec ou sans répondeur)\". Pour que cela fonctionne, suis attentivement les étapes suivantes :';

  @override
  String get sipSetupStep1 =>
      '1. ouvre la page Téléphonie > Périphériques de téléphonie dans les paramètres de ta Fritz!Box et clique sur le bouton \"Configurer un nouveau périphérique\".';

  @override
  String get sipSetupStep2 =>
      '2. choisis l\'option \"Téléphone (avec ou sans répondeur)\" et clique sur \"Suivant\".';

  @override
  String get sipSetupStep3 =>
      '3. choisis l\'option \"LAN/WLAN (téléphone IP)\", donne au téléphone le nom \"PhoneBlock\" et clique sur \"Suivant\".';

  @override
  String get sipSetupStep4 =>
      '4. donne maintenant le nom d\'utilisateur et le mot de passe suivants pour ton répondeur, puis clique sur \"Suivant\".';

  @override
  String get usernameHelp2 =>
      'Le nom d\'utilisateur avec lequel le répondeur PhoneBlock se connecte à ta Fritz!Box.';

  @override
  String get passwordHelp3 =>
      'Le mot de passe que le répondeur PhoneBlock utilise pour se connecter à ta Fritz!Box. PhoneBlock a généré un mot de passe sûr pour toi.';

  @override
  String get sipSetupStep5 =>
      '5. le numéro d\'appel demandé maintenant n\'a pas d\'importance, le répondeur PhoneBlock ne passe pas d\'appels de manière active, il ne reçoit que des appels SPAM. Le numéro d\'appel est à nouveau désélectionné à l\'étape 9. Clique ici simplement sur \"Continuer\".';

  @override
  String get sipSetupStep6 =>
      '6. choisis \"accepter tous les appels\" et clique sur \"Suivant\". De toute façon, le répondeur PhoneBlock n\'accepte les appels que si le numéro de l\'appelant figure sur la liste de blocage. En même temps, PhoneBlock n\'accepte jamais d\'appels provenant de numéros qui figurent dans ton répertoire téléphonique normal.';

  @override
  String get sipSetupStep7 =>
      '7. tu vois un résumé. Les réglages sont (presque) terminés, clique sur \"Appliquer\".';

  @override
  String get sipSetupStep8 =>
      '8. dans la liste des appareils de téléphonie, tu vois maintenant \"PhoneBlock\". Il manque encore quelques réglages que tu ne pourras faire qu\'ultérieurement. Clique donc sur le crayon d\'édition dans la ligne du répondeur PhoneBlock.';

  @override
  String get sipSetupStep9 =>
      '9. dans le champ \"Appels sortants\", sélectionne la dernière option (vide), car PhoneBlock n\'effectue jamais d\'appels sortants et le répondeur n\'a donc pas besoin de numéro pour les appels sortants.';

  @override
  String get sipSetupStep10 =>
      '10. sélectionne l\'onglet \"Données de connexion\". Confirme le message en cliquant sur \"Appliquer\". Sélectionne maintenant l\'option \"Autoriser la connexion depuis Internet\" pour que le répondeur PhoneBlock puisse se connecter à ta Fritz!Box depuis le PhoneBlock-Cloud. Tu dois saisir à nouveau le mot de passe du répondeur téléphonique (voir ci-dessus) dans le champ \"Mot de passe\" avant de cliquer sur \"Appliquer\". Pour cela, supprime d\'abord les astérisques qui se trouvent dans le champ.';

  @override
  String get sipSetupStep11 =>
      '11. un message s\'affiche, indiquant que des connexions payantes peuvent être établies via l\'accès à Internet. Tu peux le confirmer en toute confiance car, premièrement, PhoneBlock n\'établit jamais de connexions actives, deuxièmement, PhoneBlock a généré pour toi un mot de passe sûr (voir ci-dessus), de sorte que personne d\'autre ne peut se connecter et troisièmement, tu as désactivé les connexions sortantes à l\'étape 9. Selon les paramètres de ta Fritz!Box, tu dois encore confirmer le réglage sur un téléphone DECT directement connecté à la Fritz!Box.';

  @override
  String get sipSetupStep12 =>
      '12. maintenant, tout est terminé. Clique sur Retour pour revenir à la liste des appareils de téléphonie. Tu peux maintenant activer ton répondeur avec le bouton ci-dessous.';

  @override
  String get tryingToRegisterAnswerbot =>
      'Essayer d\'enregistrer un répondeur téléphonique';

  @override
  String get answerbotRegistrationFailed =>
      'Échec de l\'enregistrement du répondeur téléphonique';

  @override
  String registrationFailed(String message) {
    return 'Echec de l\'enregistrement : $message';
  }

  @override
  String get answerbotRegisteredSuccess =>
      'Ton répondeur PhoneBlock a été enregistré avec succès. Les prochains appelants spam peuvent maintenant parler longuement avec PhoneBlock. Si tu veux tester toi-même la messagerie PhoneBlock, compose le numéro interne de l\'appareil de téléphonie \"PhoneBlock\" que tu as installé. Le numéro interne commence en général par \"**\".';
}
