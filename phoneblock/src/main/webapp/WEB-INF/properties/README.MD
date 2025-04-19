# Properties for internationalized templates

This folder contains sub-directories for each supported language. Each language directory contains 
a properties file with text contents for each page of the PhoneBlock web site.

The properties for language `de` are automatically extracted from the corresponding German templates, 
since German is the native language, PhoneBlock was developed for. Properties of all other languages
are automatically translated with deepl.com from the German variant. Those properties are then used
to automatically synthesize the templates of the non-`de` variants of the templates.

The tooling for automatic translation of templates and properties can be found here:
https://github.com/haumacher/web-translate