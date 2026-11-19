# Licensing and attribution review

## Verified upstream information

The official `ivpn/mailx` repository identifies itself as GPL-3.0 licensed: <https://github.com/ivpn/mailx>. The upstream license text is available at <https://github.com/ivpn/mailx/blob/main/LICENSE.md>.

## Local snapshot issue

The supplied Android-client archive has no top-level `LICENSE`/`LICENSE.md`, while its Cargo workspace previously declared `MIT` and named a separate repository. Those facts do not establish the license of every file in this archive, nor do they establish whether any file is copied or derived from upstream source.

For that reason this update removes the unverified workspace-level Cargo `MIT` declaration rather than silently relicensing the whole project. It does **not** add a new project-wide license. Before publishing binaries or source, complete a file-level provenance and license review, retain all third-party notices, and choose the correct license for original contributions. If a component is a derivative of GPL-3.0-covered upstream code, comply with the applicable GPL-3.0 obligations for that covered work.

This is an engineering note, not legal advice. Do not describe this Android app as official or endorsed by IVPN. Do not use IVPN logos or imply IVPN authorship without permission.
