<img align="right" src="phoneblock/src/main/webapp/assets/img/app-logo.svg"/>

# PhoneBlock
Der Spam-Filter für dein Telefon (FRITZ!Box oder Mobil) - keine Kosten, keine Zusatzhardware

* [PhoneBlock-Homepage](https://phoneblock.net/)
* [Installation SPAM-Anrufbeantworter in der Fritz!Box](https://phoneblock.net/phoneblock/anrufbeantworter)
* [Installation Blocklist-Telefonbuch in der Fritz!Box](https://phoneblock.net/phoneblock/setup)
* [Installation PhoneBlock Mobile auf Android-Mobiltelefonen](https://phoneblock.net/phoneblock/setup-android)
* [Installation PhoneBlock auf iPhones](https://phoneblock.net/phoneblock/setup-iphone)
* [Berichte über aktuelle Werbeanrufer](https://phoneblock.net/phoneblock/status)
* [Fragen und Antworten zu PhoneBlock](https://phoneblock.net/phoneblock/faq)
* [API für Entwickler](https://phoneblock.net/phoneblock/api)
* [Spende für den Betrieb von PhoneBlock](https://phoneblock.net/phoneblock/support)

## Wie funktioniert PhoneBlock?

Mit dem PhoneBlock Adressbuch kannst Du unerwünschte Anrufe an Deiner FRITZ!Box oder Mobiltelefon blockieren. 
Das Adressbuch wird von der PhoneBlock-Community gepflegt, indem Telefonnummern von Spam-Anrufern (Werbung, Betrugsversuche, Umfragen) gesammelt und allen zur Verfügung gestellt werden. 
Dieses Adressbuch kann entweder in deinem Internet-Router "FRITZ!Box" als Sperrliste eingerichtet werden, steuert den PhoneBlock-Anrufbeantworter, oder kann mit Hilfe einer App (z.B. [PhoneBlock Mobile](https://play.google.com/store/apps/details?id=de.haumacher.phoneblock_mobile)) auf Deinem Mobiltelefon unerwünschte Anrufe unterdrücken oder markieren.
Sobald eine Nummer zu der Sperrliste hinzugefügt wird, weist entweder deine FRITZ!Box Anrufer mit dieser Nummer automatisch ab, oder der PhoneBlock-Anrufbeantworter geht ran. 
Das Telefon bleibt stumm.

Solltest du trotzdem einen unerwünschten Anruf erhalten, weil die Nummer noch nicht in der Sperrliste ist, kannst du die Nummer ganz einfach melden und damit die Sperrliste ergänzen. 
Einen unerwünschten Anrufer meldest Du, indem Du entweder einen neuen Kontakt im PhoneBlock-Adressbuch deiner FRITZ!Box erstellst, die Nummer über die App PhoneBlock Mobile als SPAM markierst, oder die Nummer auf der [PhoneBlock-Wenseite](https://phoneblock.net) suchst und negativ bewertest. 
Sobald du dies getan hast, werden Anrufe von dieser neuen Nummer bei Dir und allen anderen Mitgliedern der PhoneBlock-Community sofort abgewiesen.

## Was sind die Voraussetzungen?

PhoneBlock kann sowohl für einen "Festnetzanschluss" als auch auf dem Mobiltelefon verwendet werden.
Bei der Installation fürs Festnetz meldest Du den FritzBox-Anrufbeantworter als VOIP-Telefon an Deinem Internet-Router an. Dieser geht dann bei SPAM-Anrufen sofort ran und verwickelt den Anrufer in ein nettes Gespräch.  
Alternativ kannst Du die Blockliste in den Internet-Router als CardDAV-Telefonbuch integrieren (hierfür wird eine "FRITZ!Box" mit FRITZ!OS 07.29 oder neuer benötigt). 
Du erstellst dann eine Regel für die Rufbehandlung, die Anrufer aus dem Blocklisten-Telefonbuch direkt abweist, oder an Deinen Anrufbeantworter weiterleitet.  
Beim Import der Blockliste direkt in den Router kann allerdings nur ein Teil der Blockliste verwendet werden, da es eine Größenbeschränkung für Telefonbücher in der FRITZ!Box gibt. 
Besser ist daher der FritzBox-Anrufbeantworter, weil dieser die gesamte Blockliste verwenden kann.
Am einfachsten ist die Verwendung auf Deinem Android Mobiltelefon. Hier installierst Du einfach die PhoneBlock Mobile App und unerwünschte Anrufer werden direkt abgewiesen. 
Auf Deinem iPhone kannst Du die Blockliste als Internet-Telefonbuch abonieren und siehst bei einem Anruf sofort, dass es sich nicht lohnt das Gespräch anzunehmen.  

## Bist du bereit, loszulegen?

Nutze PhoneBlock live unter: https://phoneblock.net/ - keine Kosten, keine Zusatzhardware notwendig.

Du möchtest den PhoneBlock-Anrufbeantworter lieber bei Dir zu Hause betreiben - kein Problem versucht's am besten mit dem Docker-Image: https://hub.docker.com/r/phoneblock/answerbot


# English Version
The Spam-Filter for your Phone (FRITZ!Box oder mobile) - no cost, no additional hardware

* [PhoneBlock homepage](https://phoneblock.net/)
* [Installing SPAM answering machine in the Fritz!Box](https://phoneblock.net/phoneblock/anrufbeantworter)
* [Installing block list phone book in the Fritz!Box](https://phoneblock.net/phoneblock/setup)
* [Installing PhoneBlock Mobile on Android mobile phones](https://phoneblock.net/phoneblock/setup-android)
* [Installing PhoneBlock on iPhones](https://phoneblock.net/phoneblock/setup-iphone)
* [Reports on current advertising callers](https://phoneblock.net/phoneblock/status)
* [Questions and answers about PhoneBlock](https://phoneblock.net/phoneblock/faq)
* [API for developers](https://phoneblock.net/phoneblock/api)
* [Donate to support PhoneBlock](https://phoneblock.net/phoneblock/support)

## How does PhoneBlock work?

With the PhoneBlock address book, you can block unwanted calls to your FRITZ!Box or cell phone. 
The address book is maintained by the PhoneBlock community, which collects phone numbers of spam callers (advertising, fraud attempts, surveys) and makes them available to everyone. 
This address book can either be set up as a block list in your “FRITZ!Box” internet router, which controls the PhoneBlock answering machine, or it can be used to suppress or mark unwanted calls on your cell phone with the help of an app (e.g., [PhoneBlock Mobile](https://play.google.com/store/apps/details?id=de.haumacher.phoneblock_mobile)).
As soon as a number is added to the block list, either your FRITZ!Box automatically rejects callers with this number, or the PhoneBlock answering machine picks up. 
The phone remains silent.

If you still receive an unwanted call because the number is not yet on the block list, you can easily report the number and add it to the block list. 
You can report an unwanted caller by either creating a new contact in the PhoneBlock address book of your FRITZ!Box, marking the number as SPAM via the PhoneBlock Mobile app, or searching for the number on the [PhoneBlock website](https://phoneblock.net) and giving it a negative rating. 
Once you have done this, calls from this new number will be immediately rejected by you and all other members of the PhoneBlock community.


## What are the requirements?

PhoneBlock can be used for both landlines and cell phones.
When installing it for a landline, you register the FritzBox answering machine as a VOIP phone on your internet router. It then immediately answers spam calls and engages the caller in a friendly conversation.  
Alternatively, you can integrate the block list into the Internet router as a CardDAV phone book (this requires a “FRITZ!Box” with FRITZ!OS 07.29 or newer). 
You then create a rule for call handling that either rejects callers from the block list phone book directly or forwards them to your answering machine.  
However, when importing the block list directly into the router, only part of the block list can be used because there is a size limit for phone books in the FRITZ!Box. 
The FritzBox answering machine is therefore better because it can use the entire block list.
The easiest way to use it is on your Android mobile phone. Simply install the PhoneBlock Mobile app and unwanted callers will be rejected immediately.
On your iPhone, you can subscribe to the block list as an Internet phone book and see immediately when a call comes in that it is not worth answering.  


## Ready to get started?

Start using PhoneBlock live at: https://phoneblock.net/ - no cost, no additional hardware required.

You would prefer to operate the answering machine at home - no problem, just try the Docker image: https://hub.docker.com/r/phoneblock/answerbot
