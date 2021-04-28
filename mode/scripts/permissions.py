import sys, re

from urllib.request import urlopen
from bs4 import BeautifulSoup

def get_soup(url):
    page = urlopen(url)
    soup = BeautifulSoup(page, features="lxml")
    return soup

def parse_all(soup):    
    print('Parsing all permissions...')
    table = soup.find('table', { 'id': 'constants', 'class' : 'responsive constants' })
    entries = table.find_all('tr')
    str_list = ''
    for entry in entries:
        if not entry or not entry.attrs: continue
        info = entry.find('td', {'width':'100%'})
        if info:
            name = info.find('code').find('a').contents[0]
            pieces = []
            deprecated = False
            for piece in info.find('p').contents:
                piece_str = re.sub('\s+', ' ', str(piece)).strip()
                if '<code>' in piece_str:
                    piece_str = piece.find('a').contents[0].strip()
                if '<em>' in piece_str and 'This constant was deprecated' in piece_str:
                    deprecated = True
                pieces += [piece_str]
            if name and pieces and not deprecated:
                desc = ' '.join(pieces).strip().replace('"', '\\"')
                str_list += (',' if str_list else '') + '\n    "' + name + '", "' + desc + '"'
    str_list = 'static final String[] listing = {' + str_list + '\n  };\n'
    return str_list

def replace_all(source, str_list):
    print('Replacing old permissions...')
    idx0 = source.find('static final String[] listing = {')
    idx1 = source[idx0:].find('  };')
    return source[:idx0] + str_list + source[idx0+idx1+5:]

def parse_danger(soup):
    print('Parsing dangerous permissions...')
    entries = soup.find_all(lambda tag:tag.name == "div" and
                            len(tag.attrs) == 1 and
                            "data-version-added" in tag.attrs)
    str_list = ''    
    for entry in entries:        
        name = entry.find('h3').contents[0]
        items = entry.find_all('p')
        for item in items:
            text = item.getText().strip()
            if 'Protection level:' in text and 'dangerous' in text:
                str_list += (',' if str_list else '') + '\n    "' + name + '"'
    str_list = 'static final String[] dangerous = {' + str_list + '\n  };\n'
    return str_list

def replace_danger(source, str_list):
    print('Replacing dangerous permissions...')
    idx0 = source.find('static final String[] dangerous = {')
    idx1 = source[idx0:].find('  };')
    return source[:idx0] + str_list + source[idx0+idx1+5:]

java_file = '../src/processing/mode/android/Permissions.java'
ref_url = 'https://developer.android.com/reference/android/Manifest.permission.html'

print('Reading Android reference...')
soup = get_soup(ref_url)

print('Reading Permissions.java...')
with open(java_file, 'r') as f:
    source = f.read()

all_list = parse_all(soup)
source = replace_all(source, all_list)

danger_list = parse_danger(soup)
source = replace_danger(source, danger_list)

print('Writing Permissions.java...')
with open(java_file, 'w') as f:
    f.write(source)

print('Done.')