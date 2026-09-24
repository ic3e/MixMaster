#!/usr/bin/env python3
"""
What the compiler would have told us, for a project that is built somewhere else.

Everything here is a mistake this app has actually shipped: a string that existed in one
language only, an import that was never added, a brace that never closed. Run it before
every push.

    python3 tools/preflight.py
"""
import glob
import re
import sys
import xml.etree.ElementTree as ET

SRC = 'app/src/main/java'
RES = 'app/src/main/res'

# Members of the layout scopes and of Modifier itself: these need no import.
SCOPE_MEMBERS = {
    'weight', 'align', 'alignBy', 'alignByBaseline', 'matchParentSize',
    'animateItemPlacement', 'animateEnterExit', 'then', 'composed',
}
# Operators the compiler resolves without the name ever appearing in the body.
IMPLICIT = {'getValue', 'setValue', 'provideDelegate', 'plusAssign', 'minusAssign'}


def kotlin_files():
    return sorted(glob.glob(f'{SRC}/**/*.kt', recursive=True))


def strip_code(body):
    """Comments and string literals out of the way, so brackets can be counted."""
    body = re.sub(r'"""(.|\n)*?"""', '""', body)
    body = re.sub(r'(?<!\\)"(\\.|[^"\\\n])*"', '""', body)
    body = re.sub(r'/\*(.|\n)*?\*/', '', body)
    return re.sub(r'//[^\n]*', '', body)


def modifier_chain_names(src):
    """The names in every `Modifier.a(...).b(...)` chain."""
    for match in re.finditer(r'\bModifier\b', src):
        i = match.end()
        while i < len(src):
            step = re.match(r'\s*\.(\w+)\(', src[i:])
            if not step:
                break
            j, depth = i + step.end() - 1, 0
            while j < len(src):
                if src[j] == '(':
                    depth += 1
                elif src[j] == ')':
                    depth -= 1
                    if depth == 0:
                        break
                j += 1
            yield step.group(1)
            i = j + 1


def resource_names(folder, tag):
    found = set()
    for path in glob.glob(f'{RES}/{folder}/*.xml'):
        root = ET.parse(path).getroot()
        if root.tag == 'resources':
            found |= {e.get('name') for e in root if e.tag == tag}
    return found


def main():
    problems = []

    # 1 · every string the code asks for exists, in every language the app speaks
    kinds = ('string', 'plurals', 'string-array')
    base = {k: resource_names('values', k) for k in kinds}
    for folder in ('values-et', 'values-fi'):
        have = {k: resource_names(folder, k) for k in kinds}
        for kind in kinds:
            missing = {n for n in base[kind] - have[kind] - {'app_name'} if not n.startswith('conwic_')}
            extra = have[kind] - base[kind]
            if missing:
                problems.append(f'{folder}: no {kind} {sorted(missing)}')
            if extra:
                problems.append(f'{folder}: {kind} not in the default locale {sorted(extra)}')
    for path in kotlin_files():
        src = open(path, encoding='utf-8').read()
        for ref in re.finditer(r'R\.(string|plurals|array)\.(\w+)', src):
            kind = {'string': 'string', 'plurals': 'plurals', 'array': 'string-array'}[ref.group(1)]
            if ref.group(2) not in base[kind]:
                problems.append(f'{path}: R.{ref.group(1)}.{ref.group(2)} is not defined')

    # 2 · modifiers resolve: imported, a scope member, or written in this package
    package_extensions = {}
    for path in kotlin_files():
        src = open(path, encoding='utf-8').read()
        package = re.search(r'^package (\S+)', src, re.M)
        if package:
            package_extensions.setdefault(package.group(1), set()).update(
                re.findall(r'fun (?:\w+\.)?Modifier\.(\w+)', src),
            )
    for path in kotlin_files():
        src = open(path, encoding='utf-8').read()
        package = re.search(r'^package (\S+)', src, re.M)
        imported = {l.split('.')[-1].strip() for l in src.split('\n') if l.startswith('import ')}
        near = package_extensions.get(package.group(1) if package else '', set())
        for name in set(modifier_chain_names(src)):
            if name in SCOPE_MEMBERS or name in imported or name in near:
                continue
            problems.append(f'{path}: Modifier.{name}( has no import')

    # 3 · imports that are not used, duplicated, and brackets that never close
    for path in kotlin_files():
        lines = open(path, encoding='utf-8').read().split('\n')
        imports = [l[len('import '):].strip() for l in lines if l.startswith('import ')]
        body = '\n'.join(l for l in lines if not l.startswith(('import ', 'package ')))
        duplicates = {i for i in imports if imports.count(i) > 1}
        if duplicates:
            problems.append(f'{path}: imported twice {sorted(duplicates)}')
        for name in (i.split('.')[-1] for i in imports):
            if name in IMPLICIT or name == '*':
                continue
            if not re.search(r'\b' + re.escape(name) + r'\b', body):
                problems.append(f'{path}: unused import {name}')
        counted = strip_code(body)
        for opener, closer in (('{', '}'), ('(', ')'), ('[', ']')):
            if counted.count(opener) != counted.count(closer):
                problems.append(
                    f'{path}: {opener}{closer} do not balance '
                    f'({counted.count(opener)} to {counted.count(closer)})',
                )

    for problem in problems:
        print(problem)
    print(f'--- {len(problems)} problem(s)')
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())
