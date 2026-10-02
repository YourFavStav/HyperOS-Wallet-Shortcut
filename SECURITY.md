# Security

This app does not process payment credentials, card data, NFC transactions, or Google Wallet data. Its shortcut activity only launches the installed Google Wallet activity.

## Signing keys

Never commit Android signing keys, keystores, passwords, or `keystore.properties`. The repository `.gitignore` excludes common signing-key formats.

## Reporting

If you find a security issue, avoid posting secrets or private keys in a public issue. Contact the repository maintainer privately if a private reporting channel is available.
