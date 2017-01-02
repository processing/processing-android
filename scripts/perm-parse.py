''' How to use
1. Go to https://developer.android.com/reference/android/Manifest.permission.html and select desired API level.
2. Save page as Webpage complete
3. Run parse.py filename
4. Use output to set the updated permissions listing in Permissions.java
'''

import sys, re

from BeautifulSoup import BeautifulSoup

filename = sys.argv[1]
data = open(filename,'r').read()


soup = BeautifulSoup(data)
table = soup.find('table', { 'id': 'constants', 'class' : 'responsive constants' })

entries = table.findAll('tr')
print('  static final String[] listing = {')
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
            print '    "' + name + '", "' + desc + '",'
print('  };')