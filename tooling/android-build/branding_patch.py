"""Change display copy only; retain upstream namespaces, links, and attribution."""
import re

BRAND_WORD = re.compile(r'\bNuvio(?:TV)?\b')
STRING = re.compile(r'"(?:\\.|[^"\\])*"', re.S)
RESOURCE = re.compile(r'(<string\b[^>]*\bname="([^"]+)"[^>]*>)(.*?)(</string>)', re.S)

def copy(text, label):
    return BRAND_WORD.sub(label, text)

def apply(root, brand):
    label = 'STCC' if brand == 'stcc' else 'Petalix'
    for path in (root/'app/src').rglob('strings.xml'):
        def resource(match):
            prefix, name, value, suffix = match.groups()
            if re.search(r'license|attribution|copyright|credit|source_code', name, re.I):
                return match.group(0)
            return prefix + (label if name == 'app_name' else copy(value, label)) + suffix
        path.write_text(RESOURCE.sub(resource, path.read_text()))
    for path in (root/'app/src').rglob('*.kt'):
        if any('/'+part+'/' in str(path) for part in ['test','androidTest','data','domain','core','petalix','updater']) or path.name.endswith(('Repository.kt','Preferences.kt','ViewModel.kt')):
            continue
        def literal(match):
            text = match.group(0)
            # Integration identifiers and attribution remain linked to upstream.
            if any(value in text.lower() for value in ['http', 'com.nuvio', 'license', 'copyright', 'gpl', 'homelessbrian']):
                return text
            return copy(text, label)
        path.write_text(STRING.sub(literal, path.read_text()))
