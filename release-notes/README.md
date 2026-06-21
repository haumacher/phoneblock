# PhoneBlock Server – Release Notes

One file per released server version (`<version>.md`), mirroring the
layout of the dongle firmware notes in
`phoneblock-dongle/firmware/release-notes/`. GitHub lists the directory
automatically, so there is deliberately no hand-maintained index here.

Each file starts with a `# PhoneBlock <version>` heading and an italic
release date, followed by `##` sections grouping the changes (Key
Features, API, Web App, Bug Fixes, …).

Maintenance:

- Add a new `<version>.md` per release. Older files stay untouched.

## Release Frequency

PhoneBlock follows a continuous deployment model with frequent releases:

- **Major releases** (X.Y.0): New features, API changes, significant improvements
- **Minor releases** (X.Y.Z): Bug fixes, translations, small enhancements
- **Development snapshots**: Active development between releases

## Getting Updates

- **Production**: https://phoneblock.net/phoneblock/
- **Test Environment**: https://phoneblock.net/pb-test/
- **GitHub**: https://github.com/haumacher/phoneblock
- **Docker Hub**: Available for Answer Bot

## Migration Notes

### Upgrading to 1.8.0+

- Mobile app users: Install PhoneBlock Mobile from app stores
- API consumers: Update to use Bearer token authentication
- Review new `/api/account`, `/api/blacklist`, `/api/whitelist` endpoints

### Upgrading to 1.7.10+

- Web access may require proof-of-work for unauthenticated users
- CardDAV and mobile login excluded from proof-of-work

## Support

- **Issues**: https://github.com/haumacher/phoneblock/issues
- **Documentation**: See INTEGRATIONS.md, JNDI-CONFIGURATION.md, CLAUDE.md
- **API Specification**: https://phoneblock.net/phoneblock/api/phoneblock.json
