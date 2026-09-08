#!/usr/bin/env python3
"""Launch an offline test world using the installed HMCL libraries and complete enabled mod set.

No launcher accounts are read. Original worlds, options and configs are never opened for writing.
Requires a built ChatImage JAR and an existing local HMCL 26.2 Fabric instance.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import uuid
import shutil
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--username', default='ChatImageTest')
parser.add_argument('--connect')
parser.add_argument('--bridge-port', type=int)
parser.add_argument('--instance', type=Path, required=True)
parser.add_argument('--game-dir', type=Path, required=True)
parser.add_argument('--jar', type=Path, required=True)
parser.add_argument('--java-home', type=Path, required=True)
args = parser.parse_args()
instance, game_dir, jar = args.instance.resolve(), args.game_dir.resolve(), args.jar.resolve()
assert instance != game_dir, 'Test directory must differ from the user instance'
assert jar.is_file()
root = instance.parent.parent
metadata = json.loads((instance / (instance.name + '.json')).read_text())
assert metadata['mainClass'] == 'net.fabricmc.loader.impl.launch.knot.KnotClient'
assert metadata['javaVersion']['majorVersion'] == 25

def allowed(item):
    rules = item.get('rules')
    result = not rules
    for rule in rules or []:
        target = rule.get('os', {})
        if target.get('name', 'osx') not in ('osx', 'universal'):
            continue
        if target.get('arch', 'arm64') not in ('aarch64', 'arm64'):
            continue
        result = rule['action'] == 'allow'
    return result

assert platform.system() == 'Darwin' and platform.machine() == 'arm64', 'This local launcher targets macOS arm64'
classpath = []
for library in metadata['libraries']:
    if not allowed(library):
        continue
    artifact = library.get('downloads', {}).get('artifact', {}).get('path')
    if artifact is None:
        group, name, version, *classifier = library['name'].split(':')
        suffix = '-' + classifier[0] if classifier else ''
        artifact = f'{group.replace(".", "/")}/{name}/{version}/{name}-{version}{suffix}.jar'
    path = root / 'libraries' / artifact
    if not path.is_file():
        raise FileNotFoundError(path)
    classpath.append(str(path))
classpath.append(str(instance / (instance.name + '.jar')))
mods = game_dir / 'mods'
mods.mkdir(parents=True, exist_ok=True)
manifest = {}
for source in sorted((instance / 'mods').glob('*.jar')):
    if source.name.lower().startswith('chatimage-'):
        continue
    target = mods / source.name
    shutil.copy2(source, target)
    digest = hashlib.sha256(source.read_bytes()).hexdigest()
    assert hashlib.sha256(target.read_bytes()).hexdigest() == digest
    manifest[source.name] = digest
for previous in mods.glob('ChatImage-*.jar'):
    if previous.name != jar.name:
        previous.rename(previous.with_suffix('.jar.disabled'))
shutil.copy2(jar, mods / jar.name)
(game_dir / 'hmcl-mods-sha256.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
config = game_dir / 'config'
config.mkdir(exist_ok=True)
bridge = instance / 'config' / 'mcpfabric.config.json'
if bridge.exists():
    shutil.copy2(bridge, config / bridge.name)
    os.chmod(config / bridge.name, 0o600)
    if args.bridge_port:
        bridge_config = json.loads((config / bridge.name).read_text())
        bridge_config['port'] = args.bridge_port
        (config / bridge.name).write_text(json.dumps(bridge_config))
command = [str(args.java_home / 'bin/java'), '-XstartOnFirstThread', '-Xmx2G',
           '--sun-misc-unsafe-memory-access=allow', '--enable-native-access=ALL-UNNAMED',
           '-cp', os.pathsep.join(classpath), metadata['mainClass'],
           '--username', args.username, '--version', '26.2', '--gameDir', str(game_dir),
           '--assetsDir', str(root / 'assets'), '--assetIndex', metadata['assets'],
           '--uuid', str(uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:' + args.username).encode()).digest(), version=3)).replace('-', ''), '--accessToken', '0', '--versionType', 'release']
if args.connect:
    command.extend(['--quickPlayMultiplayer', args.connect])
print(f'Launching packaged {jar.name} with {len(manifest)} unchanged HMCL mod JARs', flush=True)
raise SystemExit(subprocess.call(command, cwd=game_dir))
