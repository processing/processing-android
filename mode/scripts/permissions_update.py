import sys, re

from urllib2 import urlopen
from BeautifulSoup import BeautifulSoup

def getSoup(url):
    print 'Opening', url, '...'
    page = urlopen(url)
    soup = BeautifulSoup(page)
    return soup

def parseAll():
    soup = getSoup("https://developer.android.com/reference/android/Manifest.permission.html")
    print '  parsing...'
    table = soup.find('table', { 'id': 'constants', 'class' : 'responsive constants' })
    entries = table.findAll('tr')
    strList = ''
    for entry in entries:
        if not entry or not entry.attrs: continue
        if 'absent' in entry.attrs[0][1]: continue
        info = entry.find('td', {'width':'100%'})
        if info:
            name = info.find('code').find('a').contents[0]
            pieces = []
            for piece in info.find('p').contents:
                piece_str = re.sub('\s+', ' ', str(piece)).strip()
                if '<code>' in piece_str:
                    piece_str = piece.find('a').contents[0].strip();
                pieces += [piece_str]
            if name and pieces:
                desc = ' '.join(pieces).strip().replace('"', '\\"')
                strList += (',' if strList else '') + '\n    "' + name + '", "' + desc + '"'
    strList = 'static final String[] listing = {' + strList + '\n  };\n'
    return strList

def replaceAll(source, strList):
    print '  replacing...' 
    idx0 = source.find('static final String[] listing = {')
    idx1 = source[idx0:].find('  };')
    return source[:idx0] + strList + source[idx0+idx1+5:]

def parseDanger():
    soup = getSoup("https://developer.android.com/guide/topics/security/permissions.html")
    print '  parsing...'
    table = soup.find('table')
    entries = table.findAll('tr')
    strList = ''    
    for entry in entries:
        items = entry.findAll('li')
        for item in items:
            name = item.find('code').find('a').contents[0]
            strList += (',' if strList else '') + '\n    "' + name + '"'
    strList = 'static final String[] dangerous = {' + strList + '\n  };\n'
    return strList

def replaceDanger(source, strList):
    print '  replacing...'
    idx0 = source.find('static final String[] dangerous = {')
    idx1 = source[idx0:].find('  };')
    return source[:idx0] + strList + source[idx0+idx1+5:]

print 'Reading Permissions.java...'
with open('src/processing/mode/android/Permissions.java', 'r') as f:
    source = f.read()

allList = parseAll()
source = replaceAll(source, allList)

dangerList = parseDanger()
source = replaceDanger(source, dangerList)

print 'Writing Permissions.java...'
with open('src/processing/mode/android/Permissions.java', 'w') as f:
    f.write(source)
print 'Done.'