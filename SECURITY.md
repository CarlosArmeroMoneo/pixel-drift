# Security policy

## Supported versions

Only the latest signed GitHub Release receives security fixes. Development and CI artifacts are
not official releases.

## Reporting a vulnerability

Use the repository's **Security → Report a vulnerability** flow. Do not open a public issue for a
security problem and do not attach private artwork, keystores, passwords, device backups, or raw
logs containing personal data.

Include the affected version, Android version, device model, reproduction steps, and the smallest
sanitized input needed to reproduce the problem. You should receive an acknowledgement through the
private advisory thread. Public disclosure should wait until a fix and release are available.

## Release authenticity

Official APKs are published only from Git tags by the protected `release` environment. The release
workflow builds from source, runs the complete verification suite, signs with protected secrets,
verifies the certificate, and publishes an APK plus `SHA256SUMS`.

Expected release-certificate SHA-256:

`D3:24:E9:15:EA:79:62:FC:DA:08:5D:A5:F4:67:96:0E:FD:4C:DF:56:93:D7:99:46:C1:46:0B:D3:1E:B9:B8:39`

The public certificate is checked in at
[`security/PixelDrift-release-certificate.pem`](security/PixelDrift-release-certificate.pem). The
private key and its passwords must never be committed or uploaded as public artifacts.

## Security boundaries

Pixel Drift is intentionally offline and requests no runtime permission. Imported images are
untrusted input: they are format-checked, bounded by encoded and decoded size, decoded away from the
UI thread, and copied atomically into app-private storage. Unsupported animation containers are
rejected.
