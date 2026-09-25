#!/usr/bin/env python3
"""Build and package a personal release locally; never uploads the signing key."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile


def run(*args, **kwargs):
    return subprocess.run(args, check=True, text=True, **kwargs)


def output(*args, **kwargs):
    return subprocess.check_output(args, text=True, **kwargs).strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('tag', help='Version tag, for example v0.6.0')
    args = parser.parse_args()
    if not re.fullmatch(r'v\d+\.\d+\.\d+', args.tag):
        raise SystemExit('Use a version tag such as v0.6.0.')
    root = Path(__file__).resolve().parent.parent
    os.chdir(root)
    if output('git', 'status', '--porcelain'):
        raise SystemExit('Commit reviewed changes first; release source must match a clean HEAD.')
    commit = output('git', 'rev-parse', 'HEAD')
    config = (root / 'app/build.gradle.kts').read_text()
    version = re.search(r'versionName\s*=\s*"([^"]+)"', config).group(1)
    code = int(re.search(r'versionCode\s*=\s*(\d+)', config).group(1))
    if args.tag != 'v' + version:
        raise SystemExit('Tag must match versionName in app/build.gradle.kts.')
    tag = subprocess.run(['git', 'rev-parse', '--verify', '--quiet', args.tag + '^{commit}'],
                         text=True, capture_output=True)
    if tag.returncode == 0 and tag.stdout.strip() != commit:
        raise SystemExit('That tag already points to another commit; do not move published tags.')

    env = dict(os.environ)
    env.setdefault('ANDROID_USER_HOME', str(root / '.tools/android-user'))
    key = Path(env['ANDROID_USER_HOME']) / 'debug.keystore'
    if not key.is_file():
        raise SystemExit('Original debug.keystore is missing. Restore it privately before releasing.')
    if not env.get('JAVA_HOME'):
        for home in sorted((root / '.tools/jdk21').glob('*/Contents/Home')):
            if (home / 'bin/jlink').is_file():
                env['JAVA_HOME'] = str(home)
                break
    sdk = env.get('ANDROID_HOME') or env.get('ANDROID_SDK_ROOT')
    properties = root / 'local.properties'
    if properties.is_file():
        match = re.search(r'^sdk\.dir=(.+)$', properties.read_text(), re.MULTILINE)
        if match:
            sdk = match.group(1).strip().replace('\\:', ':').replace('\\ ', ' ')
    if not sdk:
        raise SystemExit('Set ANDROID_HOME or configure sdk.dir in local.properties.')
    tools = Path(sdk) / 'build-tools/36.0.0'
    for name in ('apksigner', 'aapt'):
        if not (tools / name).is_file():
            raise SystemExit('Install Android Build-Tools 36.0.0 before packaging.')

    # Use tracked source only. Fail if a private/generated file was accidentally tracked.
    tracked = output('git', 'ls-files', '-z').split('\0')
    for name in tracked:
        path = Path(name)
        if name.startswith(('.tools/', 'artifacts/')) or path.name == 'local.properties' or path.suffix in ('.keystore', '.jks', '.key', '.pem', '.apk', '.aab'):
            raise SystemExit('Unexpected private/generated tracked file: ' + name)
    notes = root / '.github/release-notes' / (args.tag + '.md')
    if not notes.is_file():
        raise SystemExit('Add reviewed release notes at .github/release-notes/' + args.tag + '.md')
    destination = root / 'artifacts/releases' / args.tag
    destination.mkdir(parents=True, exist_ok=True)
    with (destination / 'build.log').open('w') as log:
        result = subprocess.run(['sh', 'scripts/verify.sh'], env=env, stdout=log, stderr=subprocess.STDOUT)
    if result.returncode:
        raise SystemExit('Verification failed; see ' + str(destination / 'build.log'))

    apk = root / 'app/build/outputs/apk/debug/app-debug.apk'
    signature = output(str(tools / 'apksigner'), 'verify', '--print-certs', str(apk), env=env)
    signers = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)', signature)
    expected = (root / '.github/release-signing.sha256').read_text().strip().lower()
    if signers != [expected]:
        raise SystemExit('APK signer differs from the existing installation. No release assets packaged.')
    metadata = json.loads((apk.parent / 'output-metadata.json').read_text())
    element = metadata['elements'][0]
    badging = output(str(tools / 'aapt'), 'dump', 'badging', str(apk), env=env)
    if (metadata['applicationId'] != 'app.socialpause' or element['versionName'] != version
            or element['versionCode'] != code or "name='app.socialpause'" not in badging
            or "versionName='" + version + "'" not in badging
            or "versionCode='" + str(code) + "'" not in badging
            or 'android.permission.INTERNET' in badging):
        raise SystemExit('APK metadata does not match this offline SocialPause release.')
    if output('git', 'rev-parse', 'HEAD') != commit or output('git', 'status', '--porcelain'):
        raise SystemExit('Source changed during the build; commit and verify again.')

    shutil.copy2(apk, destination / 'SocialPause.apk')
    run('git', 'archive', '--format=zip', '--prefix=SocialPause/',
        '--output=' + str(destination / 'SocialPause-source.zip'), commit)
    with zipfile.ZipFile(destination / 'SocialPause-source.zip') as source:
        if source.testzip() is not None:
            raise SystemExit('Source ZIP verification failed.')
    (destination / 'BUILD-INFO.txt').write_text(
        'SocialPause ' + version + '\nVersion code: ' + str(code) + '\nTag: ' + args.tag
        + '\nSource commit: ' + commit + '\nPackage: app.socialpause'
        + '\nBuild type: personal-install debug APK\nSigning certificate SHA-256: ' + expected
        + '\nValidation: scripts/verify.sh (engine checks, debug assembly, Android lint) passed.'
        + '\nSamsung device acceptance is separate from build verification.\n')
    assets = ('SocialPause.apk', 'SocialPause-source.zip', 'BUILD-INFO.txt')
    (destination / 'SHA256SUMS.txt').write_text(''.join(
        hashlib.sha256((destination / name).read_bytes()).hexdigest() + '  ' + name + '\n'
        for name in assets))
    print('Verified release assets: ' + str(destination))
    print('Source commit: ' + commit)
    print('Upload only SocialPause.apk, SocialPause-source.zip, BUILD-INFO.txt and SHA256SUMS.txt.')


if __name__ == '__main__':
    try:
        main()
    except (OSError, subprocess.CalledProcessError, KeyError, ValueError) as error:
        sys.exit(str(error))
